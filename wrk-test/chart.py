#!/usr/bin/env python3

import matplotlib.pyplot as plt
import pandas as pd
import sys
import math

colorseq = plt.cm.tab10.colors
plt.rcParams['axes.prop_cycle'] = plt.cycler('color', colorseq)

df = pd.read_csv(sys.stdin, sep='\t')

def map_account_id(id):
    id = id.removesuffix("@example.com")
    if id == "admin":
        id = "(control)"
    elif id == "ANONYMOUS":
        id = "(none)"
    return id


# Latencies plot

def get_latencies(df):
    return df.filter(regex='p[0-9.]+ latency')/1000

by_trial = df.groupby(by='trial')
fig, plots = plt.subplots(len(by_trial), 1, sharex=True, figsize=(6, 8))
for trial, df in by_trial:
    ax = plots[trial]
    ax.set_yscale('log')
    ax.set_ylabel('Trial ' + str(trial + 1), rotation=-90, labelpad=14)
    ax.grid(which='major', linestyle='dashed', lw=0.5)
    ax.grid(which='minor', linestyle='dotted', lw=0.3)
    ax.yaxis.set_label_position("right")
    ax.yaxis.set_major_formatter(plt.ScalarFormatter())
    latencies = get_latencies(df).iloc[:, ::-1]
    log_ymin = math.log10(latencies.min().min())
    log_ymax = math.log10(latencies.max().max())
    log_ymargin = (log_ymax - log_ymin) * 0.1
    ymin = 10**(log_ymin-log_ymargin)
    ymax = 10**(log_ymax+log_ymargin)
    ax.set_ylim(ymin=ymin, ymax=ymax)
    ax.plot([map_account_id(id) for id in df['account ID']], latencies)
    plt.setp(ax.get_xticklabels(), rotation=30, ha="right", rotation_mode="anchor")

fig.legend([l.removesuffix(' latency (us)') for l in latencies.columns], loc='center', bbox_to_anchor=(1.05, 0.5))
fig.supylabel('Request latency (milliseconds)')
fig.savefig("latencies.png", bbox_inches='tight', dpi=400)


# Allow/deny request rate bar graph

fig, ax = plt.subplots()
ax.set_ylabel('Requests per second')
ax.set_yscale('log')
ax.yaxis.set_major_formatter(plt.ScalarFormatter())
ax.grid(which='major', linestyle='dashed', lw=0.5, axis='y')
ax.grid(which='minor', linestyle='dotted', lw=0.3, axis='y')

x = pd.Series(range(len(df['account ID'])))
ax.set_xticks(x + 0.3, [map_account_id(id) for id in df['account ID']], rotation=30, ha="right", rotation_mode="anchor")

offset = 0
handles = []
for trial, df in by_trial:
    handles.append(ax.bar(x + offset, df['allow rate (per second)'], width=0.15, label="allowed", color=colorseq[0])[0])
    handles.append(ax.bar(x + offset, df['deny rate (per second)'], bottom=df['allow rate (per second)'], width=0.15, label="denied", color=colorseq[1])[0])
    offset += 0.2

fig.legend(handles[:2], ['allowed', 'denied'], loc='center', bbox_to_anchor=(1.0, 0.5))
fig.savefig("rates.png", bbox_inches='tight', dpi=400)
