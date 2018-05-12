#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os

from filter import *


def my_mapper():
    """
    MapReduce mapping function. {article title: view count, day}

    """
    os.environ["PYTHONIOENCODING"] = "utf-8"
    current_file = os.environ["mapreduce_map_input_file"]
    current_file = re.search('pageviews.*.gz', current_file).group(0)
    _, yyyymmdd, _ = current_file.strip().split('-')
    day = yyyymmdd[6:]

    for line in sys.stdin:
        try:
            line = line_filter([line])
            if len(line) != 0:
                line = line[0].split()
                key = line[1]
                count = str(int(line[2]))
                value = ' '.join([count, day])
                print(key + '\t' + value)
        except Exception as e:
            print(e)
            continue


def line_filter(lines):
    lines = domain_filter(lines)
    lines = hex_filter(lines)
    lines = prefix_filter(lines)
    lines = lowercase_filter(lines)
    lines = disambig_filter(lines)
    lines = suffix_filter(lines)
    lines = special_pages_filter(lines)

    return lines


if __name__ == '__main__':
    my_mapper()


