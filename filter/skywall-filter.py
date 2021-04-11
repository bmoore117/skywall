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
        self.urlRegex = re.compile(r'((https?|ftp|file):)?(\\)?/(\\)?/[-a-zA-Z0-9+&@#/%?=~_|!:,.;\\]*[-a-zA-Z0-9+&@#/%=~_|\\]')

        self.blockedPhrases = set()

        self.binaryContentTypes = ['video', 'audio', 'image', 'octet', 'pdf', 'zip', 'ms', 'soap', 'mpeg']

        script_file_path = os.path.join(os.getenv('LOCALAPPDATA'), "SkyWall", "filter")
        hosts_dir = os.path.join(script_file_path, "hosts")
        hosts_file = os.path.join(hosts_dir, "hosts.json")

        self.load_file(hosts_file)

        patterns = ["*.json"]
        ignore_patterns = []
        ignore_directories = True
        case_sensitive = True
        handler = PatternMatchingEventHandler(patterns, ignore_patterns, ignore_directories, case_sensitive)
        handler.on_modified = self.on_modified

        observer = Observer()
        observer.schedule(handler, hosts_dir, recursive=False)
        observer.start()

    def on_modified(self, event):
        self.load_file(event.src_path)

    def load_file(self, src_path):
        with self.lock:
            with open(src_path) as f:
                data = json.load(f)
                hosts = data.get('hosts')

                # first we remove all sites no longer whitelisted in the file
                sites_to_remove = []
                for site in self.siteKnownHosts:
                    if site not in hosts:
                        sites_to_remove.append(site)
                for site in sites_to_remove:
                    self.siteKnownHosts.pop(site)

                # then we add all newly whitelisted sites
                # the idea of doing it all this way is not to remove any discovered hosts
                for allowedSite in hosts:
                    if allowedSite not in self.siteKnownHosts:
                        self.siteKnownHosts[allowedSite] = set()

                self.blockedUrls.clear()
                self.blockedUrls.update(data.get('blockedHosts', set()))

                self.blockedPhrases.clear()
                phrases_str = data.get('blockedPhrases', set())
                for phrase in phrases_str:
                    try:
                        self.blockedPhrases.add(re.compile(phrase))
                    except:
                        ctx.log.error("Error compiling phrase: " + phrase + ". " + traceback.format_exc())

                self.lockdownActive = data.get('lockdownActive', False)
            print("Allowed url paths: " + str(self.siteKnownHosts.keys()))
            print("Blocked url paths: " + str(self.blockedUrls))
            print("Blocked phrases: " + str(self.blockedPhrases))

    def is_url_path_blocked(self, url):
        for urlPath in self.blockedUrls:
            if urlPath in url:
                return True  # blocked
        return False

    def is_url_path_whitelisted(self, url):
        if self.lockdownActive:
            ctx.log.info("Lockdown active, blocking all media")
            return True  # blocked

        # /images/dogs in /images/dogs/pugs,
        # /images/dogs not in /images/cats, -> /images/dogs = urlPath, /images/cats = url
        for urlPath in self.siteKnownHosts.keys():
            if urlPath in url:
                return True
        return False

    def is_origin_known_for_referer(self, referer_url, origin_url):
        if referer_url is None:
            return False

        if self.lockdownActive:
            ctx.log.info("Lockdown active, blocking all media")
            return False  # blocked

        # is referer a root or subtree of a root, save that root
        site_root = None
        for urlPath in self.siteKnownHosts.keys():
            if urlPath in referer_url.geturl():
                site_root = urlPath

        if site_root is not None:
            ctx.log.info("Referer matches whitelisted root: " + site_root)
            if origin_url.hostname in self.siteKnownHosts[site_root]:
                return True
        return False

    @staticmethod
    def get_url_dir_path(origin_url):
        ctx.log.info("Path is: " + origin_url.path)
        last_index_of_slash = origin_url.path.rfind("/") + 1
        last_index_of_dot = origin_url.path.rfind(".") + 1
        if last_index_of_slash > last_index_of_dot:  # there is no file extension ending, take whole path
            return origin_url.hostname + origin_url.path
        else:
            return origin_url.hostname + origin_url.path[:last_index_of_slash]

    def filter_phrase(self, flow):
        lower_text = flow.response.text.lower()
        for phrase in self.blockedPhrases:
            match = re.search(phrase, lower_text)
            if match is not None:
                ctx.log.info("Filtering custom phrase: " + phrase.pattern)
                flow.response = http.HTTPResponse.make(200,
                                                       "<html><body>Filtering phrase: " + phrase.pattern
                                                       + "</body></html>", {"content-type": "text/html"})
                return True
        return False

    @staticmethod
    def process_src(src, origin_url):
        if "://" not in src:
            if src.startswith("//"):
                full_url = origin_url.scheme + ":" + src
            elif src.startswith("/"):
                full_url = origin_url.scheme + "://" + origin_url.hostname + src
            else:
                full_url = src
            return urlparse(full_url)
        else:
            return urlparse(src)

    def process_tag_src(self, tag, origin_url, site_root_key):
        parsed_urls = []
        if site_root_key is not None:
            if tag.has_attr('src'):
                parsed_url = self.process_src(tag['src'], origin_url)
                parsed_urls.append(parsed_url)
                self.siteKnownHosts[site_root_key].add(parsed_url.hostname)
            if tag.has_attr('data-src'):
                parsed_url = self.process_src(tag['data-src'], origin_url)
                parsed_urls.append(parsed_url)
                self.siteKnownHosts[site_root_key].add(parsed_url.hostname)
            if tag.has_attr('href'):
                parsed_url = self.process_src(tag['href'], origin_url)
                parsed_urls.append(parsed_url)
                self.siteKnownHosts[site_root_key].add(parsed_url.hostname)
        return parsed_urls

    @staticmethod
    def accept_match(match: str, content_type: str) -> bool:
        if 'script' in content_type:
            js_factors = 0
            if ";" in match:
                js_factors = js_factors + 1
            if ":" in match:
                js_factors = js_factors + 1
            if ")" in match:
                js_factors = js_factors + 1
            if "(" in match:
                js_factors = js_factors + 1
            if "&&" in match:
                js_factors = js_factors + 1
            if "||" in match:
                js_factors = js_factors + 1
            if js_factors >= 2:
                return False
        return True

    @staticmethod
    def sanitize_match(match: str, content_type: str) -> str:
        sanitized_match = match
        if "\\u0" in sanitized_match:
            if sanitized_match.endswith("\\"):
                # the regex probably picked up a url in a string with escaped quotes, and got the slash but not
                # the quote: ...\"
                sanitized_match = sanitized_match[:-1]
            sanitized_match = sanitized_match.encode().decode('unicode-escape')
        if "\\" in sanitized_match:
            sanitized_match = sanitized_match.replace("\\", "")
        if 'css' in content_type:
            if sanitized_match.endswith(")"):
                # very likely the regex picked up a url from css url() function, and grabbed the close paren
                sanitized_match = sanitized_match[:-1]
            elif ");" in sanitized_match:
                idx = sanitized_match.index(");")
                sanitized_match = sanitized_match[:idx]
        return sanitized_match

    def process_file(self, script, origin_url, site_root_key, content_type):
        if script is None:
            return

        for match in re.finditer(self.urlRegex, script):
            if not self.accept_match(match.group(0), content_type):
                continue

            sanitized_match = self.sanitize_match(match.group(0), content_type)
            url = self.process_src(sanitized_match, origin_url)

            if url.hostname is not None:
                if site_root_key is not None:
                    self.siteKnownHosts[site_root_key].add(url.hostname)
                self.mediaUrlsQueue.append(url.geturl())

    def extract_urls(self, text, content_type, origin_url, referer_url, origin_known_for_referer):
        hash_val = hash(text)
        if hash_val in self.processedPages:
            ctx.log.info("Document hash previously seen, skipping url extraction")
            return

        if origin_known_for_referer:
            site_root_url = referer_url
        else:
            site_root_url = origin_url
        ctx.log.info("Site root url: " + site_root_url.geturl())

        site_root_key = None
        for urlPath in self.siteKnownHosts.keys():
            if urlPath in site_root_url.geturl():
                site_root_key = urlPath
        ctx.log.info("Site root key: " + str(site_root_key))

        if 'urlencoded' in content_type:
            decoded_text = unquote_plus(text)
        else:
            decoded_text = text

        if 'html' in content_type:
            soup = BeautifulSoup(decoded_text, 'html.parser')
            for a in soup.find_all("a"):
                parsed_urls = self.process_tag_src(a, site_root_url, site_root_key)
                for url in parsed_urls:
                    self.mediaUrlsQueue.append(url.geturl())
            for img in soup.find_all('img'):
                parsed_urls = self.process_tag_src(img, site_root_url, site_root_key)
                for url in parsed_urls:
                    self.mediaUrlsQueue.append(url.geturl())
            for vid in soup.find_all('video'):
                parsed_urls = self.process_tag_src(vid, site_root_url, site_root_key)
                for url in parsed_urls:
                    self.mediaUrlsQueue.append(url.geturl())
            for link in soup.find_all('link'):
                parsed_urls = self.process_tag_src(link, site_root_url, site_root_key)
                for url in parsed_urls:
                    self.mediaUrlsQueue.append(url.geturl())
            for frame in soup.find_all('iframe'):
                self.process_tag_src(frame, site_root_url, site_root_key)
            for script in soup.find_all('script'):
                if script.has_attr('src'):
                    self.process_tag_src(script, site_root_url, site_root_key)
                else:
                    self.process_file(script.string, site_root_url, site_root_key, 'text/javascript')
            for style in soup.find_all('style'):
                if style.has_attr('src'):
                    self.process_tag_src(style, site_root_url, site_root_key)
                else:
                    self.process_file(style.string, site_root_url, site_root_key, 'text/css')
            for code in soup.find_all('code'):
                self.process_file(code.string, site_root_url, site_root_key, content_type)
        else:
            self.process_file(decoded_text, site_root_url, site_root_key, content_type)

        self.discoveredSafeMediaUrls.clear()
        self.discoveredSafeMediaUrls.update(self.mediaUrlsQueue)
        self.processedPages.add(hash_val)

    def content_type_non_binary(self, content_type):
        for binaryType in self.binaryContentTypes:
            if binaryType in content_type:
                return False  # content type is binary
        return True  # content type non binary

    # def request(self, flow: mitmproxy.http.HTTPFlow):
    #    ctx.log.info(time.ctime(time.time()))
    #    ctx.log.info("Request is: " + flow.request.pretty_url)
    #    ctx.log.info('') # always leave a blank line before next log request entry

    @staticmethod
    def log_end_of_response(start_nanos: int, method_name: str):
        final_time = time.perf_counter_ns() - start_nanos
        ctx.log.info("Method: " + method_name + " runtime: " + str(final_time) + " ns")
        ctx.log.info('')  # always leave a blank line before next log request entry

    def response(self, flow: mitmproxy.http.HTTPFlow):
        start_nanos = time.perf_counter_ns()
        ctx.log.info(time.ctime(time.time()))
        content_type = flow.response.headers.get("Content-Type", "null").lower()
        ctx.log.info("Content type is " + content_type + " for url: " + flow.request.pretty_url)

        origin_url = urlparse(flow.request.pretty_url)
        url = self.get_url_dir_path(origin_url)

        ctx.log.info("Constructed url is: " + url)

        if self.is_url_path_blocked(url):
            ctx.log.info("Blocking url: " + url)
            flow.response = http.HTTPResponse.make(404)
            self.log_end_of_response(start_nanos, "response")
            return

        referer = flow.request.headers.get('referer', None)
        ctx.log.info("Got referer: " + str(referer))
        referer_url = None
        if referer is not None:
            referer_url = urlparse(referer)

        exact_url_known = origin_url.geturl() in self.discoveredSafeMediaUrls
        origin_known_for_referer = self.is_origin_known_for_referer(referer_url, origin_url)

        if exact_url_known or origin_known_for_referer or self.is_url_path_whitelisted(url):
            if exact_url_known:
                ctx.log.info("Exact url known")
            elif origin_known_for_referer:
                ctx.log.info("Referer in discovered referrers for origin")
            else:
                ctx.log.info("Request part of whitelisted subtree")
            # don't want to slow down loading of these by invoking flow.response.text
            if self.content_type_non_binary(content_type):
                try:
                    if flow.response.text:  # flow.response.text checks to make sure there is actually text (not binary)
                        if 'css' not in content_type and 'script' not in content_type:
                            if not self.filter_phrase(flow):
                                self.extract_urls(flow.response.text, content_type, origin_url, referer_url,
                                                  origin_known_for_referer)
                        else:
                            # css and scripts are always to be allowed, as most phrase matches are false positives
                            # but we have to make sure we check them for urls
                            self.extract_urls(flow.response.text, content_type, origin_url, referer_url,
                                              origin_known_for_referer)
                except ValueError:  # flow.response.text can fail here, typically on woff font responses
                    ctx.log.error("Received message with undecipherable encoding, forwarding as-is. " + traceback.format_exc())
        else:
            if origin_known_for_referer:
                ctx.log.info("Referer not in discovered referrers for origin")
            if 'video' in content_type or 'image' in content_type and 'svg' not in content_type:
                ctx.log.info("Blocking detected media, returning 404")
                flow.response = http.HTTPResponse.make(404)
            else:
                if self.content_type_non_binary(content_type) and 'woff' not in origin_url.geturl():
                    try:
                        if flow.response.text:
                            if 'css' not in content_type and 'script' not in content_type:
                                if not self.filter_phrase(flow):
                                    match = self.dataUrlRegex.subn('', flow.response.text)
                                    if match[1] > 0:
                                        flow.response.text = match[0]
                                        ctx.log.info("Removed image data urls")
                    except ValueError:
                        ctx.log.error("Received message with undecipherable encoding, forwarding as-is. " + traceback.format_exc())

        self.log_end_of_response(start_nanos, "response")

    def websocket_message(self, flow: mitmproxy.websocket.WebSocketFlow):
        """
            Called when a WebSocket message is received from the client or
            server. The most recent message will be flow.messages[-1]. The
            message is user-modifiable. Currently there are two types of
            messages, corresponding to the BINARY and TEXT frame types.
        """
        ctx.log.info(time.ctime(time.time()))
        if flow.messages[-1].type == Opcode.BINARY:
            if flow.server_conn.sni not in self.siteKnownHosts and flow.server_conn.address[0] not in self.siteKnownHosts:
                ctx.log.info("Blocking binary frame type, setting empty body")
                flow.messages[-1].content = ""
        ctx.log.info('')  # leave blank line


addons = [
    JarvisFilter()
]
