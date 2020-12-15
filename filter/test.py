import urllib.parse

with open("C:\\Users\\moore\\Downloads\\get_video_info") as f:
    data = f.read()
    text = urllib.parse.unquote_plus(data)
    print(text)
