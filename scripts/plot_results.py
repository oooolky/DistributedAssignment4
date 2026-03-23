#!/usr/bin/env python3
"""
plot_results.py

Generates all graphs required for the CS6650 Assignment 4 report.

Directory structure:
  Input:  report/<mode>/CSV/run_<N>pctWrites_<timestamp>_*.csv
  Output: report/<mode>/plot_<mode_short>_<N>pctWrites_<type>.png

  Examples:
    report/lf-w5r1/plot_w5r1_1pctWrites_read_latency.png
    report/leaderless/plot_leaderless_50pctWrites_write_latency.png

Usage:
  python3 scripts/plot_results.py <mode> [--show]
  mode: lf-w5r1 | lf-w1r1 | lf-w1r5 | lf-w3r3 | leaderless
"""

import sys
import os
import glob
import pandas as pd
import matplotlib
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker

matplotlib.use("Agg")

WRITE_PERCENTAGES = [1, 10, 50, 90]
ANNOTATION_BOX = dict(boxstyle="round", facecolor="wheat", alpha=0.5)


def load_csv(csv_dir, write_pct, suffix):
    pattern = os.path.join(csv_dir, f"run_{write_pct}pctWrites_*{suffix}")
    files = sorted(glob.glob(pattern))
    if not files:
        print(f"  [WARN] No file: {pattern}")
        return pd.Series(dtype=float)
    return pd.read_csv(files[-1]).iloc[:, 0].dropna()


def generate_latency_plot(data, title, output_file, show=False):
    if data.empty:
        print(f"  [SKIP] {title}")
        return

    p50, p95, p99 = data.quantile(0.50), data.quantile(0.95), data.quantile(0.99)
    mean, maximum = data.mean(), data.max()

    fig, (ax_hist, ax_cdf) = plt.subplots(1, 2, figsize=(14, 5))
    fig.suptitle(title, fontsize=13, fontweight="bold")

    # Histogram
    ax_hist.hist(data, bins=60, color="steelblue", edgecolor="white", linewidth=0.3)
    ax_hist.set_xlabel("Latency (ms)", fontsize=12)
    ax_hist.set_ylabel("Request count", fontsize=12)
    ax_hist.set_title("Latency Distribution")
    ax_hist.grid(True, alpha=0.3)
    ax_hist.xaxis.set_major_locator(ticker.MaxNLocator(integer=True))
    for lbl, val, col in [("P50", p50, "green"), ("P95", p95, "orange"), ("P99", p99, "red")]:
        ax_hist.axvline(val, color=col, linestyle="--", linewidth=1.2, label=f"{lbl}: {val:.0f} ms")
    ax_hist.legend(fontsize=9)
    ax_hist.annotate(
        f"Mean:  {mean:.1f} ms\nP50:   {p50:.0f} ms\nP95:   {p95:.0f} ms\n"
        f"P99:   {p99:.0f} ms\nMax:   {maximum:.0f} ms\nn:     {len(data):,}",
        xy=(0.98, 0.98), xycoords="axes fraction", va="top", ha="right",
        fontsize=9, bbox=ANNOTATION_BOX)

    # CDF
    sorted_data = data.sort_values().reset_index(drop=True)
    cdf = pd.Series(range(1, len(sorted_data) + 1)) / len(sorted_data)
    ax_cdf.plot(sorted_data.values, cdf.values, linewidth=1.5, color="steelblue")
    ax_cdf.set_xlabel("Latency (ms)", fontsize=12)
    ax_cdf.set_ylabel("Cumulative probability", fontsize=12)
    ax_cdf.set_title("Latency CDF (long tail)")
    ax_cdf.set_ylim(0, 1.02)
    ax_cdf.grid(True, alpha=0.3)
    ax_cdf.yaxis.set_major_formatter(ticker.PercentFormatter(xmax=1, decimals=0))
    for lbl, pct, val, col in [("P50", 0.50, p50, "green"), ("P95", 0.95, p95, "orange"), ("P99", 0.99, p99, "red")]:
        ax_cdf.axhline(pct, color=col, linestyle=":", linewidth=1, alpha=0.7)
        ax_cdf.axvline(val, color=col, linestyle="--", linewidth=1.2, label=f"{lbl}: {val:.0f} ms")
    ax_cdf.legend(fontsize=9)

    plt.tight_layout()
    os.makedirs(os.path.dirname(output_file), exist_ok=True)
    plt.savefig(output_file, dpi=150, bbox_inches="tight")
    print(f"  Plot saved to {output_file}")
    print(f"  Mean={mean:.1f}ms | P50={p50:.0f}ms | P95={p95:.0f}ms | P99={p99:.0f}ms | n={len(data):,}")
    if show:
        plt.show()
    plt.close()


def generate_interval_plot(data, title, output_file, show=False):
    if data.empty:
        print(f"  [SKIP] {title}")
        return

    p99, median, mean = data.quantile(0.99), data.median(), data.mean()
    clipped = data[data <= p99]

    fig, ax = plt.subplots(figsize=(12, 6))
    ax.hist(clipped, bins=50, color="darkorange", edgecolor="white", linewidth=0.3)
    ax.set_xlabel("Interval between accesses to the same key (ms)", fontsize=12)
    ax.set_ylabel("Count", fontsize=12)
    ax.set_title(title, fontsize=13, fontweight="bold")
    ax.grid(True, alpha=0.3)
    ax.axvline(median, color="navy", linestyle="--", linewidth=1.5, label=f"Median: {median:.0f} ms")
    ax.legend(fontsize=9)
    ax.annotate(
        f"Mean:   {mean:.1f} ms\nMedian: {median:.0f} ms\nP99:    {p99:.0f} ms\n"
        f"n:      {len(data):,}\n(x-axis capped at P99)",
        xy=(0.98, 0.98), xycoords="axes fraction", va="top", ha="right",
        fontsize=9, bbox=ANNOTATION_BOX)

    plt.tight_layout()
    os.makedirs(os.path.dirname(output_file), exist_ok=True)
    plt.savefig(output_file, dpi=150, bbox_inches="tight")
    print(f"  Plot saved to {output_file}")
    print(f"  Mean={mean:.1f}ms | Median={median:.0f}ms | P99={p99:.0f}ms | n={len(data):,}")
    if show:
        plt.show()
    plt.close()


def main():
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help"):
        print("Usage: python3 scripts/plot_results.py <mode> [--show]")
        print("  mode: lf-w5r1 | lf-w1r1 | lf-w1r5 | lf-w3r3 | leaderless")
        sys.exit(0 if "--help" in sys.argv else 1)

    mode       = sys.argv[1]
    show_plots = "--show" in sys.argv
    # Strip the "lf-" prefix for cleaner filenames: lf-w5r1 → w5r1
    mode_short = mode.replace("lf-", "")

    if show_plots:
        matplotlib.use("TkAgg")

    csv_dir    = os.path.join("report", mode, "CSV")
    graphs_dir = os.path.join("report", mode, "plot_graph")

    if not os.path.isdir(csv_dir):
        print(f"Error: CSV directory not found: {csv_dir}")
        sys.exit(1)

    print(f"\nGenerating graphs for: {mode}  (short: {mode_short})")
    print(f"CSV dir    : {csv_dir}")
    print(f"Graphs dir : {graphs_dir}\n")

    for write_pct in WRITE_PERCENTAGES:
        read_pct    = 100 - write_pct
        ratio_label = f"W={write_pct}% / R={read_pct}%"
        file_prefix = f"plot_{mode_short}_{write_pct}pctWrites"

        print(f"\n{'─' * 50}")
        print(f"  {mode}  |  {ratio_label}")
        print(f"{'─' * 50}")

        read_latencies  = load_csv(csv_dir, write_pct, "_read_latencies.csv")
        write_latencies = load_csv(csv_dir, write_pct, "_write_latencies.csv")
        key_intervals   = load_csv(csv_dir, write_pct, "_key_intervals.csv")

        generate_latency_plot(
            data=read_latencies,
            title=f"{mode}  |  {ratio_label}  —  Read Latency",
            output_file=os.path.join(graphs_dir, f"{file_prefix}_read_latency.png"),
            show=show_plots,
        )
        generate_latency_plot(
            data=write_latencies,
            title=f"{mode}  |  {ratio_label}  —  Write Latency",
            output_file=os.path.join(graphs_dir, f"{file_prefix}_write_latency.png"),
            show=show_plots,
        )
        generate_interval_plot(
            data=key_intervals,
            title=f"{mode}  |  {ratio_label}  —  Key Access Interval Distribution",
            output_file=os.path.join(graphs_dir, f"{file_prefix}_key_intervals.png"),
            show=show_plots,
        )

    print(f"\nPlot graphs saved to: {graphs_dir}/\n")


if __name__ == "__main__":
    main()
