#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import json
from os import environ
from os.path import abspath, dirname, join


def create_blacklist():
    json_path = join(abspath(dirname(__file__)), "namespaces.json")
    try:
        with open(json_path) as f:
            data = json.loads(f.read())
    except Exception:
        raise Exception('error loading from {}'.format(json_path))

    black = []
    namespace_dict = data['query']['namespaces']
    for k, v in namespace_dict.items():
        if k != '0':
            word = v['*'].lower().replace(" ", "_")
            print(word + ':')
            black += [word + ':']
    
    return black


def main():
    environ["PYTHONIOENCODING"] = "utf-8"
    create_blacklist()


if __name__ == '__main__':
    main()
