#!/usr/bin/env python3
"""
plot_results.py

Generates all graphs required for the CS6650 Assignment 4 report.
Style based on the project's existing plot.py conventions.

For each of the 4 read/write ratios (W=1%, 10%, 50%, 90%), generates:
  1. Read latency  — histogram showing distribution + CDF showing long tail
  2. Write latency — histogram showing distribution + CDF showing long tail
  3. Key access interval — histogram showing temporal locality of the workload

Usage:
    python3 scripts/plot_results.py <mode> [--show]

Arguments:
    mode    One of: lf-w5r1 | lf-w1r1 | lf-w3r3 | leaderless
    --show  Open each plot interactively after saving (omit on headless EC2)

Examples:
    python3 scripts/plot_results.py lf-w5r1
    python3 scripts/plot_results.py leaderless --show

Output:
    graphs/<mode>/<mode>_w<N>pct_read_latency.png
    graphs/<mode>/<mode>_w<N>pct_write_latency.png
    graphs/<mode>/<mode>_w<N>pct_key_intervals.png

CSV input files (produced by CsvExporter.java):
    results/<mode>/run_<N>pctWrites_<timestamp>_read_latencies.csv
    results/<mode>/run_<N>pctWrites_<timestamp>_write_latencies.csv
    results/<mode>/run_<N>pctWrites_<timestamp>_key_intervals.csv
"""

import sys
import os
import glob
import pandas as pd
import matplotlib
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker

# Use non-interactive backend by default; switched to interactive if --show is passed.
matplotlib.use("Agg")

# The four write percentages required by the assignment.
WRITE_PERCENTAGES = [1, 10, 50, 90]

# Annotation box style carried over from plot.py.
ANNOTATION_BOX_STYLE = dict(boxstyle="round", facecolor="wheat", alpha=0.5)


# ─────────────────────────────────────────────────────────────────────────────
# Data loading
# ─────────────────────────────────────────────────────────────────────────────

def load_csv(results_dir, write_pct, suffix):
    """
    Loads the most recent CSV file matching the CsvExporter naming pattern.
    Returns an empty Series if no matching file is found.
    """
    pattern = os.path.join(results_dir, f"run_{write_pct}pctWrites_*{suffix}")
    files = sorted(glob.glob(pattern))
    if not files:
        print(f"  [WARN] No file found: {pattern}")
        return pd.Series(dtype=float)
    dataframe = pd.read_csv(files[-1])
    return dataframe.iloc[:, 0].dropna()


# ─────────────────────────────────────────────────────────────────────────────
# Plot 1 & 2 — Latency (histogram + CDF side by side)
# ─────────────────────────────────────────────────────────────────────────────

def generate_latency_plot(data, title, output_file, show=False):
    """
    Generates a side-by-side histogram and CDF for a latency dataset.

    The histogram shows the shape of the distribution.
    The CDF exposes the long tail (P50, P95, P99 marked on both panels).

    Mirrors the style of plot.py: wheat annotation box, grid at alpha=0.3,
    console statistics printed in a formatted block.
    """
    if data.empty:
        print(f"  [SKIP] No data for: {title}")
        return

    p50     = data.quantile(0.50)
    p95     = data.quantile(0.95)
    p99     = data.quantile(0.99)
    mean    = data.mean()
    maximum = data.max()

    fig, (ax_hist, ax_cdf) = plt.subplots(1, 2, figsize=(14, 5))
    fig.suptitle(title, fontsize=13, fontweight="bold")

    # ── Left panel: histogram ────────────────────────────────────────────────
    ax_hist.hist(data, bins=60, color="steelblue", edgecolor="white", linewidth=0.3)
    ax_hist.set_xlabel("Latency (ms)", fontsize=12)
    ax_hist.set_ylabel("Request count", fontsize=12)
    ax_hist.set_title("Latency Distribution")
    ax_hist.grid(True, alpha=0.3)
    ax_hist.xaxis.set_major_locator(ticker.MaxNLocator(integer=True))

    for label, value, color in [("P50", p50, "green"),
                                 ("P95", p95, "orange"),
                                 ("P99", p99, "red")]:
        ax_hist.axvline(value, color=color, linestyle="--", linewidth=1.2,
                        label=f"{label}: {value:.0f} ms")
    ax_hist.legend(fontsize=9)

    # Stats annotation box — same style as plot.py.
    stats_text = (f"Mean:  {mean:.1f} ms\n"
                  f"P50:   {p50:.0f} ms\n"
                  f"P95:   {p95:.0f} ms\n"
                  f"P99:   {p99:.0f} ms\n"
                  f"Max:   {maximum:.0f} ms\n"
                  f"n:     {len(data):,}")
    ax_hist.annotate(stats_text, xy=(0.98, 0.98), xycoords="axes fraction",
                     verticalalignment="top", horizontalalignment="right",
                     fontsize=9, bbox=ANNOTATION_BOX_STYLE)

    # ── Right panel: CDF (long tail is visible here) ─────────────────────────
    sorted_data = data.sort_values().reset_index(drop=True)
    cdf_values  = pd.Series(range(1, len(sorted_data) + 1)) / len(sorted_data)

    ax_cdf.plot(sorted_data.values, cdf_values.values, linewidth=1.5, color="steelblue")
    ax_cdf.set_xlabel("Latency (ms)", fontsize=12)
    ax_cdf.set_ylabel("Cumulative probability", fontsize=12)
    ax_cdf.set_title("Latency CDF (long tail)")
    ax_cdf.set_ylim(0, 1.02)
    ax_cdf.grid(True, alpha=0.3)
    ax_cdf.yaxis.set_major_formatter(ticker.PercentFormatter(xmax=1, decimals=0))

    for pct_label, pct_val, value, color in [("P50", 0.50, p50, "green"),
                                              ("P95", 0.95, p95, "orange"),
                                              ("P99", 0.99, p99, "red")]:
        ax_cdf.axhline(pct_val, color=color, linestyle=":", linewidth=1, alpha=0.7)
        ax_cdf.axvline(value,   color=color, linestyle="--", linewidth=1.2,
                       label=f"{pct_label}: {value:.0f} ms")
    ax_cdf.legend(fontsize=9)

    plt.tight_layout()
    os.makedirs(os.path.dirname(output_file), exist_ok=True)
    plt.savefig(output_file, dpi=150, bbox_inches="tight")
    print(f"  Plot saved to {output_file}")

    # Print statistics to console in the same format as plot.py.
    print(f"  LATENCY STATISTICS")
    print(f"  {'=' * 40}")
    print(f"  Mean latency : {mean:.2f} ms")
    print(f"  P50 latency  : {p50:.0f} ms")
    print(f"  P95 latency  : {p95:.0f} ms")
    print(f"  P99 latency  : {p99:.0f} ms")
    print(f"  Max latency  : {maximum:.0f} ms")
    print(f"  Sample count : {len(data):,}")
    print(f"  {'=' * 40}")

    if show:
        plt.show()
    plt.close()


# ─────────────────────────────────────────────────────────────────────────────
# Plot 3 — Key access interval distribution
# ─────────────────────────────────────────────────────────────────────────────

def generate_interval_plot(data, title, output_file, show=False):
    """
    Generates a histogram of time intervals between successive accesses to the
    same key. A distribution clustered near zero confirms that the workload
    generator produces reads and writes to the same key close together in time,
    which is required to expose stale reads.

    x-axis is capped at P99 so outliers don't collapse the histogram shape.
    """
    if data.empty:
        print(f"  [SKIP] No interval data for: {title}")
        return

    p99    = data.quantile(0.99)
    median = data.median()
    mean   = data.mean()
    clipped = data[data <= p99]

    fig, ax = plt.subplots(figsize=(12, 6))
    ax.hist(clipped, bins=50, color="darkorange", edgecolor="white", linewidth=0.3)
    ax.set_xlabel("Interval between accesses to the same key (ms)", fontsize=12)
    ax.set_ylabel("Count", fontsize=12)
    ax.set_title(title, fontsize=13, fontweight="bold")
    ax.grid(True, alpha=0.3)

    ax.axvline(median, color="navy", linestyle="--", linewidth=1.5,
               label=f"Median: {median:.0f} ms")
    ax.legend(fontsize=9)

    # Stats annotation box — same style as plot.py.
    stats_text = (f"Mean:   {mean:.1f} ms\n"
                  f"Median: {median:.0f} ms\n"
                  f"P99:    {p99:.0f} ms\n"
                  f"n:      {len(data):,}\n"
                  f"(x-axis capped at P99)")
    ax.annotate(stats_text, xy=(0.98, 0.98), xycoords="axes fraction",
                verticalalignment="top", horizontalalignment="right",
                fontsize=9, bbox=ANNOTATION_BOX_STYLE)

    plt.tight_layout()
    os.makedirs(os.path.dirname(output_file), exist_ok=True)
    plt.savefig(output_file, dpi=150, bbox_inches="tight")
    print(f"  Plot saved to {output_file}")

    print(f"  INTERVAL STATISTICS")
    print(f"  {'=' * 40}")
    print(f"  Mean interval  : {mean:.2f} ms")
    print(f"  Median interval: {median:.0f} ms")
    print(f"  P99 interval   : {p99:.0f} ms")
    print(f"  Sample count   : {len(data):,}")
    print(f"  {'=' * 40}")

    if show:
        plt.show()
    plt.close()


# ─────────────────────────────────────────────────────────────────────────────
# Entry point
# ─────────────────────────────────────────────────────────────────────────────

def main():
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help"):
        print("Usage: python3 scripts/plot_results.py <mode> [--show]")
        print("  mode:   lf-w5r1 | lf-w1r1 | lf-w3r3 | leaderless")
        print("  --show  open each plot interactively (omit on headless EC2)")
        sys.exit(0 if "--help" in sys.argv else 1)

    mode       = sys.argv[1]
    show_plots = "--show" in sys.argv

    # Switch to an interactive backend only when the user explicitly requests it.
    if show_plots:
        matplotlib.use("TkAgg")

    results_dir = os.path.join("results", mode)
    graphs_dir  = os.path.join("graphs", mode)

    if not os.path.isdir(results_dir):
        print(f"Error: results directory not found: {results_dir}")
        print(f"Run the load test first: ./scripts/run-load-test.sh {mode} <ssh-key>")
        sys.exit(1)

    print(f"\nGenerating graphs for mode: {mode}")
    print(f"Results dir : {results_dir}")
    print(f"Graphs dir  : {graphs_dir}")
    print(f"Show plots  : {show_plots}\n")

    for write_pct in WRITE_PERCENTAGES:
        read_pct    = 100 - write_pct
        ratio_label = f"W={write_pct}% / R={read_pct}%"

        print(f"\n{'─' * 50}")
        print(f"  {mode}  |  {ratio_label}")
        print(f"{'─' * 50}")

        read_latencies  = load_csv(results_dir, write_pct, "_read_latencies.csv")
        write_latencies = load_csv(results_dir, write_pct, "_write_latencies.csv")
        key_intervals   = load_csv(results_dir, write_pct, "_key_intervals.csv")

        generate_latency_plot(
            data=read_latencies,
            title=f"{mode}  |  {ratio_label}  —  Read Latency",
            output_file=os.path.join(graphs_dir, f"{mode}_w{write_pct}pct_read_latency.png"),
            show=show_plots,
        )
        generate_latency_plot(
            data=write_latencies,
            title=f"{mode}  |  {ratio_label}  —  Write Latency",
            output_file=os.path.join(graphs_dir, f"{mode}_w{write_pct}pct_write_latency.png"),
            show=show_plots,
        )
        generate_interval_plot(
            data=key_intervals,
            title=f"{mode}  |  {ratio_label}  —  Key Access Interval Distribution",
            output_file=os.path.join(graphs_dir, f"{mode}_w{write_pct}pct_key_intervals.png"),
            show=show_plots,
        )

    print(f"\n✅ All graphs saved to: {graphs_dir}/\n")


if __name__ == "__main__":
    main()
