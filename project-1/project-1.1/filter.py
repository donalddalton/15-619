#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import re
import sys
from os import environ
from os.path import abspath, dirname, join

from utils import *


def domain_filter(lines):
    """
    Filter by domain.

    Parameters
    ----------
    lines: list[str]
        List of lines. Each line is a string.
    Returns
    -------
    lines: list[str]
        List of lines. Each line is a string.
    """
    clean_lines = []
    regex_en = re.compile('^en$|^en.m$')
    regex_decode = re.compile('%3(A|a)')
    for line in lines:
        line = line.strip().split()
        if len(line) > 0 and regex_en.match(line[0]) is not None and all(x != '' for x in line) and len(line) == 4:
            if '%' in line[1]:
                line[1] = regex_decode.sub(':', decode(line[1].encode('utf-8', 'surrogateescape')))
            clean_lines += [' '.join(line)]

    return clean_lines


def hex_filter(lines):
    """
    Perform encoding of titles with consecutive hexidecimal values.

    Parameters
    ----------
    lines: list[str]
        List of lines. Each line is a string.
    Returns
    -------
    clean_lines: list[str]
        List of lines. Each line is a string.
    """

    def is_hex(x):
        try:
            bytearray.fromhex(x).decode('utf-8')
            return True
        except Exception:
            return False

    clean_lines = []
    regex = re.compile('(%[0-9a-fA-F][0-9a-fA-F]){2,}')
    for line in lines:
        line = line.split()
        regex_search = regex.search(line[1])
        if regex_search is not None:
            split = line[1][regex_search.regs[0][0]:regex_search.regs[0][1]].split('%')
            replacement = ''.join([bytearray.fromhex(x).decode('utf-8') for x in split[1:] if is_hex(x)])
            line[1] = line[1][0:regex_search.regs[0][0]] + replacement + line[1][regex_search.regs[0][1]:]

        clean_lines += [' '.join(line)]

    return clean_lines


def prefix_filter(lines):
    """
    Filter based on title prefix.

    Parameters
    ----------
    lines: list[str]
        List of lines. Each line is a string.
    Returns
    -------
    lines: list[str]
        List of lines. Each line is a string.
    """
    keep_idx = []
    black_list = get_black_list()
    black_list += ['media:']
    black_list = [re.sub('_(talk)', '', x[:-1]) for x in black_list]
    black_list = ['^' + x + '(_talk)?:' for x in black_list]
    regex = re.compile('|'.join([x for x in black_list]), re.I)
    for idx, line in enumerate(lines):
        if regex.search(line.split(' ')[1]) is None:
            keep_idx += [idx]

    lines = [lines[idx] for idx in keep_idx]

    return lines


def lowercase_filter(lines):
    """
    Filter based on case of title first letter.

    Parameters
    ----------
    lines: list[str]
        List of lines. Each line is a string.
    Returns
    -------
    lines: list[str]
        List of lines. Each line is a string.
    """
    keep_idx = []
    regex = re.compile('[a-z]')
    for idx, line in enumerate(lines):
        if regex.match(line.split(' ')[1][0]) is None:
            keep_idx += [idx]

    lines = [lines[idx] for idx in keep_idx]

    return lines


def disambig_filter(lines):
    """
    Filter out disambiguation titles.

    Parameters
    ----------
    lines: list[str]
        List of lines. Each line is a string.
    Returns
    -------
    lines: list[str]
        List of lines. Each line is a string.
    """
    keep = []
    regex = re.compile('_\(disambiguation\)$', re.I)
    for idx, line in enumerate(lines):
        if regex.search(line.split(' ')[1]) is None:
            keep += [idx]

    lines = [lines[idx] for idx in keep]

    return lines


def suffix_filter(lines):
    """
    Filter based on title suffix.

    Parameters
    ----------
    lines: list[str]
        List of lines. Each line is a string.
    Returns
    -------
    lines: list[str]
        List of lines. Each line is a string.
    """
    keep_idx = []
    file_extensions = get_file_extensions()
    regex = re.compile('|'.join(['\.' + x[1:] + '$' for x in file_extensions]), re.I)
    for idx, line in enumerate(lines):
        if regex.search(line.split(' ')[1]) is None:
            keep_idx += [idx]

    lines = [lines[idx] for idx in keep_idx]

    return lines


def special_pages_filter(lines):
    """
    Filter out special pages.

    Parameters
    ----------
    lines: list[str]
        List of lines. Each line is a string.
    Returns
    -------
    lines: list[str]
        List of lines. Each line is a string.
    """
    keep_idx = []
    special_pages = get_special_pages()
    regex = re.compile('|'.join(['^' + x + '$' for x in special_pages]))
    for idx, line in enumerate(lines):
        if regex.search(line.split(' ')[1]) is None:
            keep_idx += [idx]

    lines = [lines[idx] for idx in keep_idx]

    return lines


def make_count_dict(lines):
    """
    Resolves duplicate titles and counts number of title requests.

    Parameters
    ----------
    lines: list[str]
        List of lines. Each line is a string.
    Returns
    -------
    counts: list[[str, int]]
        List of lists. Each list of form [<title>, <requests>]
    """
    counts = {}
    for line in lines:
        line = line.split(' ')
        if line[1] not in counts.keys():
            counts[line[1]] = float(line[2])
        else:
            counts[line[1]] += float(line[2])

    counts = sorted([(v, k) for k, v in counts.items()], key=lambda x: (-x[0], x[1]), reverse=True)[::-1]

    return counts


def count_writer(counts):
    """
    Count writer.

    Parameters
    ----------
    counts: list[[str, int]]
        List of lists. Each list of form [<title>, <requests>]
    """
    path = join(abspath(join(dirname(__file__), "..")), "output")
    try:
        with open(path, mode='wt', encoding='utf-8', newline='\n', errors="surrogateescape") as f:
            for count in counts:
                f.write(count[1] + '\t' + str(int(count[0])) + '\n')
    except Exception:
        raise Exception('could not write output to {}'.format(path))


def main():
    environ["PYTHONIOENCODING"] = "utf-8"
    lines = sys.stdin.readlines()
    lines = domain_filter(lines)
    lines = hex_filter(lines)
    lines = prefix_filter(lines)
    lines = lowercase_filter(lines)
    lines = disambig_filter(lines)
    lines = suffix_filter(lines)
    lines = special_pages_filter(lines)
    counts = make_count_dict(lines)
    count_writer(counts)


if __name__ == '__main__':
    main()
