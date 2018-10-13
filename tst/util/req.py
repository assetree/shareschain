# -*- coding: utf-8 -*-

import requests
import json

def post(url,data):
    response = requests.post(url,data=data)
    return json.loads(response.text)

if __name__ == '__main__':
    post()