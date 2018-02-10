#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys


def my_reducer():
    """
    MapReduce reducing function. Aggregates hourly page view counts into daily page views.

    """
    current_article = None
    daily_views = [0] * 30

    for line in sys.stdin:
        try:
            line = line.strip()
            article, count_data = line.split('\t', 1)
            count, day = count_data.split(' ')
            count = int(count)
            day = int(day)
            if article == current_article and 0 < day < 31:
                daily_views[day - 1] += count
            else:
                thresh = 100000
                if current_article and sum(daily_views) > thresh:
                    total_views = str(sum(daily_views))
                    daily_views = '\t'.join([str(view) for view in daily_views])
                    print(total_views + '\t' + current_article + '\t' + daily_views)

                current_article = article
                daily_views = [0] * 30
                if 0 < day < 31:
                    daily_views[day - 1] += count
        except Exception as e:
            print(e)
            continue


if __name__ == '__main__':
    my_reducer()

