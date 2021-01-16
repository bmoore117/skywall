import collections
import json
import os
import re
import threading
import time
import traceback
from typing import Set, Deque
from urllib.parse import unquote_plus
from urllib.parse import urlparse

import mitmproxy.http
import mitmproxy.websocket
from bs4 import BeautifulSoup
from mitmproxy import ctx
from mitmproxy import http
from watchdog.events import PatternMatchingEventHandler
from watchdog.observers import Observer
from wsproto.frame_protocol import Opcode


class JarvisFilter:

    def __init__(self):
        self.blockedUrls = set()
        self.lockdownActive = False

        self.siteKnownHosts = {}
        self.lock = threading.Lock()

        # the idea with this is we only add to it from whitelisted pages, thus even if we're on a non-whitelisted page,
        # any link we discover on that page is safe, since it was originally discovered from a whitelisted page
        self.discoveredSafeMediaUrls: Set[str] = set()
        self.mediaUrlsQueue: Deque[str] = collections.deque(maxlen=1000)
        self.processedPages: Set[int] = set()

        # remove data:image/ urls if present - Google includes in the initial response
        # original: 'data:image/.*?base64,[A-Za-z0-9+/]+'
        self.dataUrlRegex = re.compile(r'data:image[A-Za-z0-9+/,\\;=]+')
        self.urlRegex = re.compile(r"((https?|ftp|file):)?(\\)?/(\\)?/[-a-zA-Z0-9+&@#/%?=~_|!:,.;\\]*[-a-zA-Z0-9+&@#/%=~_|\\]")

        self.blockedPhrases = set()

        self.binaryContentTypes = ['video', 'audio', 'image', 'octet', 'pdf', 'zip', 'ms', 'soap', 'mpeg']

        scriptFilePath = os.path.join(os.getenv('LOCALAPPDATA'), "SkyWall")
        hostsDir = os.path.join(scriptFilePath, "filter")
        ctx.log.info("Monitoring directory: " + str(hostsDir))
        hostsFile = os.path.join(hostsDir, "hosts.json")

        self.loadFile(hostsFile)

        patterns = ["*.json"]
        ignore_patterns = []
        ignore_directories = True
        case_sensitive = True
        handler = PatternMatchingEventHandler(patterns, ignore_patterns, ignore_directories, case_sensitive)
        handler.on_modified = self.on_modified

        observer = Observer()
        observer.schedule(handler, hostsDir, recursive=False)
        observer.start()

    def on_modified(self, event):
        self.loadFile(event.src_path)

    def loadFile(self, srcPath):
        with self.lock:
            with open(srcPath) as f:
                data = json.load(f)
                hosts = data.get('hosts')

                # first we remove all sites no longer whitelisted in the file
                sitesToRemove = []
                for site in self.siteKnownHosts:
                    if site not in hosts:
                        sitesToRemove.append(site)
                for site in sitesToRemove:
                    self.siteKnownHosts.pop(site)

                # then we add all newly whitelisted sites
                # the idea of doing it all this way is not to remove any discovered hosts
                for allowedSite in hosts:
                    if allowedSite not in self.siteKnownHosts:
                        self.siteKnownHosts[allowedSite] = set()

                self.blockedUrls.clear()
                self.blockedUrls.update(data.get('blockedHosts', set()))

                self.blockedPhrases.clear()
                phrasesStr = data.get('blockedPhrases', set())
                for phrase in phrasesStr:
                    self.blockedPhrases.add(re.compile(phrase))

                self.lockdownActive = data.get('lockdownActive', False)
            print("Allowed url paths: " + str(self.siteKnownHosts.keys()))
            print("Blocked url paths: " + str(self.blockedUrls))

    def isUrlPathBlocked(self, url):
        for urlPath in self.blockedUrls:
            if urlPath in url:
                return True  # blocked
        return False

    def isUrlPathWhitelisted(self, url):
        if self.lockdownActive:
            ctx.log.info("Lockdown active, blocking all media")
            return True  # blocked

        # /images/dogs in /images/dogs/pugs,
        # /images/dogs not in /images/cats, -> /images/dogs = urlPath, /images/cats = url
        for urlPath in self.siteKnownHosts.keys():
            if urlPath in url:
                return True
        return False

    def isOriginKnownForReferer(self, refererUrl, originUrl):
        if refererUrl is None:
            return False

        if self.lockdownActive:
            ctx.log.info("Lockdown active, blocking all media")
            return False  # blocked

        # is referer a root or subtree of a root, save that root
        siteRoot = None
        for urlPath in self.siteKnownHosts.keys():
            if urlPath in refererUrl.geturl():
                siteRoot = urlPath

        if siteRoot is not None:
            ctx.log.info("Referer matches whitelisted root: " + siteRoot)
            if originUrl.hostname in self.siteKnownHosts[siteRoot]:
                return True
        return False

    @staticmethod
    def getUrlDirPath(originUrl):
        ctx.log.info("Path is: " + originUrl.path)
        lastIndexOfSlash = originUrl.path.rfind("/") + 1
        lastIndexOfDot = originUrl.path.rfind(".") + 1
        if lastIndexOfSlash > lastIndexOfDot:  # there is no file extension ending, take whole path
            return originUrl.hostname + originUrl.path
        else:
            return originUrl.hostname + originUrl.path[:lastIndexOfSlash]

    def filterPhrase(self, flow):
        lowerText = flow.response.text.lower()
        for phrase in self.blockedPhrases:
            match = re.search(phrase, lowerText)
            if match is not None:
                ctx.log.info("Filtering custom phrase: " + phrase.pattern)
                flow.response = http.HTTPResponse.make(200,
                                                       "<html><body>Filtering phrase: " + phrase.pattern
                                                       + "</body></html>", {"content-type": "text/html"})
                return True
        return False

    @staticmethod
    def processSrc(src, originUrl):
        if "://" not in src:
            if src.startswith("//"):
                fullUrl = originUrl.scheme + ":" + src
            elif src.startswith("/"):
                fullUrl = originUrl.scheme + "://" + originUrl.hostname + src
            else:
                fullUrl = src
            return urlparse(fullUrl)
        else:
            return urlparse(src)

    def processTagSrc(self, tag, originUrl, siteRootKey):
        parsedUrls = []
        if siteRootKey is not None:
            if tag.has_attr('src'):
                parsedUrl = self.processSrc(tag['src'], originUrl)
                parsedUrls.append(parsedUrl)
                self.siteKnownHosts[siteRootKey].add(parsedUrl.hostname)
            if tag.has_attr('data-src'):
                parsedUrl = self.processSrc(tag['data-src'], originUrl)
                parsedUrls.append(parsedUrl)
                self.siteKnownHosts[siteRootKey].add(parsedUrl.hostname)
            if tag.has_attr('href'):
                parsedUrl = self.processSrc(tag['href'], originUrl)
                parsedUrls.append(parsedUrl)
                self.siteKnownHosts[siteRootKey].add(parsedUrl.hostname)
        return parsedUrls

    @staticmethod
    def acceptMatch(match: str, contentType: str) -> bool:
        if 'script' in contentType:
            jsFactors = 0
            if ";" in match:
                jsFactors = jsFactors + 1
            if ":" in match:
                jsFactors = jsFactors + 1
            if ")" in match:
                jsFactors = jsFactors + 1
            if "(" in match:
                jsFactors = jsFactors + 1
            if "&&" in match:
                jsFactors = jsFactors + 1
            if "||" in match:
                jsFactors = jsFactors + 1
            if jsFactors >= 2:
                return False
        return True

    @staticmethod
    def sanitizeMatch(match: str, contentType: str) -> str:
        sanitizedMatch = match
        if "\\u0" in sanitizedMatch:
            if sanitizedMatch.endswith("\\"):
                # the regex probably picked up a url in a string with escaped quotes, and got the slash but not
                # the quote: ...\"
                sanitizedMatch = sanitizedMatch[:-1]
            sanitizedMatch = sanitizedMatch.encode().decode('unicode-escape')
        if "\\" in sanitizedMatch:
            sanitizedMatch = sanitizedMatch.replace("\\", "")
        if 'css' in contentType:
            if sanitizedMatch.endswith(")"):
                # very likely the regex picked up a url from css url() function, and grabbed the close paren
                sanitizedMatch = sanitizedMatch[:-1]
            elif ");" in sanitizedMatch:
                idx = sanitizedMatch.index(");")
                sanitizedMatch = sanitizedMatch[:idx]
        return sanitizedMatch

    def processFile(self, script, originUrl, siteRootKey, contentType):
        if script is None:
            return

        for match in re.finditer(self.urlRegex, script):
            if not self.acceptMatch(match.group(0), contentType):
                continue

            sanitizedMatch = self.sanitizeMatch(match.group(0), contentType)
            url = self.processSrc(sanitizedMatch, originUrl)

            if url.hostname is not None:
                if siteRootKey is not None:
                    self.siteKnownHosts[siteRootKey].add(url.hostname)
                self.mediaUrlsQueue.append(url.geturl())

    def extractUrls(self, text, contentType, originUrl, refererUrl, originKnownForReferer):
        hashVal = hash(text)
        if hashVal in self.processedPages:
            ctx.log.info("Document hash previously seen, skipping url extraction")
            return
        self.processedPages.add(hashVal)

        if originKnownForReferer:
            siteRootUrl = refererUrl
        else:
            siteRootUrl = originUrl
        ctx.log.info("Site root url: " + siteRootUrl.geturl())

        siteRootKey = None
        for urlPath in self.siteKnownHosts.keys():
            if urlPath in siteRootUrl.geturl():
                siteRootKey = urlPath
        ctx.log.info("Site root key: " + str(siteRootKey))

        if 'urlencoded' in contentType:
            decodedText = unquote_plus(text)
        else:
            decodedText = text

        if 'html' in contentType:
            soup = BeautifulSoup(decodedText, 'html.parser')
            for a in soup.find_all("a"):
                parsedUrls = self.processTagSrc(a, siteRootUrl, siteRootKey)
                for url in parsedUrls:
                    self.mediaUrlsQueue.append(url.geturl())
            for img in soup.find_all('img'):
                parsedUrls = self.processTagSrc(img, siteRootUrl, siteRootKey)
                for url in parsedUrls:
                    self.mediaUrlsQueue.append(url.geturl())
            for vid in soup.find_all('video'):
                parsedUrls = self.processTagSrc(vid, siteRootUrl, siteRootKey)
                for url in parsedUrls:
                    self.mediaUrlsQueue.append(url.geturl())
            for link in soup.find_all('link'):
                parsedUrls = self.processTagSrc(link, siteRootUrl, siteRootKey)
                for url in parsedUrls:
                    self.mediaUrlsQueue.append(url.geturl())
            for frame in soup.find_all('iframe'):
                self.processTagSrc(frame, siteRootUrl, siteRootKey)
            for script in soup.find_all('script'):
                if script.has_attr('src'):
                    self.processTagSrc(script, siteRootUrl, siteRootKey)
                else:
                    self.processFile(script.string, siteRootUrl, siteRootKey, 'text/javascript')
            for style in soup.find_all('style'):
                if style.has_attr('src'):
                    self.processTagSrc(style, siteRootUrl, siteRootKey)
                else:
                    self.processFile(style.string, siteRootUrl, siteRootKey, 'text/css')
            for code in soup.find_all('code'):
                self.processFile(code.string, siteRootUrl, siteRootKey, contentType)
        else:
            self.processFile(decodedText, siteRootUrl, siteRootKey, contentType)

        self.discoveredSafeMediaUrls.clear()
        self.discoveredSafeMediaUrls.update(self.mediaUrlsQueue)

    def contentTypeNonBinary(self, contentType):
        for binaryType in self.binaryContentTypes:
            if binaryType in contentType:
                return False  # content type is binary
        return True  # content type non binary

    # def request(self, flow: mitmproxy.http.HTTPFlow):
    #    ctx.log.info(time.ctime(time.time()))
    #    ctx.log.info("Request is: " + flow.request.pretty_url)
    #    ctx.log.info('') # always leave a blank line before next log request entry

    @staticmethod
    def logEndOfResponse(startNanos: int, methodName: str):
        finalTime = time.perf_counter_ns() - startNanos
        ctx.log.info("Method: " + methodName + " runtime: " + str(finalTime) + " ns")
        ctx.log.info('')  # always leave a blank line before next log request entry

    def response(self, flow: mitmproxy.http.HTTPFlow):
        startNanos = time.perf_counter_ns()
        ctx.log.info(time.ctime(time.time()))
        contentType = flow.response.headers.get("Content-Type", "null").lower()
        ctx.log.info("Content type is " + contentType + " for url: " + flow.request.pretty_url)

        originUrl = urlparse(flow.request.pretty_url)
        url = self.getUrlDirPath(originUrl)

        ctx.log.info("Constructed url is: " + url)

        if self.isUrlPathBlocked(url):
            ctx.log.info("Blocking url: " + url)
            flow.response = http.HTTPResponse.make(404)
            self.logEndOfResponse(startNanos, "response")
            return

        referer = flow.request.headers.get('referer', None)
        ctx.log.info("Got referer: " + str(referer))
        refererUrl = None
        if referer is not None:
            refererUrl = urlparse(referer)

        exactUrlKnown = originUrl.geturl() in self.discoveredSafeMediaUrls
        originKnownForReferer = self.isOriginKnownForReferer(refererUrl, originUrl)

        if exactUrlKnown or originKnownForReferer or self.isUrlPathWhitelisted(url):
            if exactUrlKnown:
                ctx.log.info("Exact url known")
            elif originKnownForReferer:
                ctx.log.info("Referer in discovered referrers for origin")
            else:
                ctx.log.info("Request is whitelisted")
            # don't want to slow down loading of these by invoking flow.response.text
            if self.contentTypeNonBinary(contentType):
                try:
                    if flow.response.text:  # flow.response.text checks to make sure there is actually text (not binary)
                        if 'css' not in contentType and 'script' not in contentType:
                            if not self.filterPhrase(flow):
                                self.extractUrls(flow.response.text, contentType, originUrl, refererUrl,
                                                 originKnownForReferer)
                        else:
                            # css and scripts are always to be allowed, as most phrase matches are false positives
                            # but we have to make sure we check them for urls
                            self.extractUrls(flow.response.text, contentType, originUrl, refererUrl,
                                             originKnownForReferer)
                except ValueError:  # flow.response.text can fail here, typically on woff font responses
                    ctx.log.info("Logging exception: " + traceback.format_exc())
                    ctx.log.info("Received message with undecipherable encoding, forwarding as-is")
        else:
            if originKnownForReferer:
                ctx.log.info("Referer not in discovered referrers for origin")
            if 'video' in contentType or 'image' in contentType and 'svg' not in contentType:
                ctx.log.info("Media detected, returning 404")
                flow.response = http.HTTPResponse.make(404)
            else:
                if self.contentTypeNonBinary(contentType) and 'woff' not in originUrl.geturl():
                    try:
                        if flow.response.text:
                            if 'css' not in contentType and 'script' not in contentType:
                                if not self.filterPhrase(flow):
                                    match = self.dataUrlRegex.subn('', flow.response.text)
                                    if match[1] > 0:
                                        flow.response.text = match[0]
                                        ctx.log.info("Removed image data urls")
                    except ValueError:
                        ctx.log.info("Logging exception: " + traceback.format_exc())
                        ctx.log.info("Received message with undecipherable encoding, forwarding as-is")

        self.logEndOfResponse(startNanos, "response")

    def websocket_message(self, flow: mitmproxy.websocket.WebSocketFlow):
        """
            Called when a WebSocket message is received from the client or
            server. The most recent message will be flow.messages[-1]. The
            message is user-modifiable. Currently there are two types of
            messages, corresponding to the BINARY and TEXT frame types.
        """
        ctx.log.info(time.ctime(time.time()))
        if flow.server_conn.address[0] not in self.siteKnownHosts:
            if flow.messages[-1].type == Opcode.BINARY:
                ctx.log.info("Blocking binary frame type")
                flow.messages[-1].content = ""
        ctx.log.info('')  # leave blank line


addons = [
    JarvisFilter()
]
