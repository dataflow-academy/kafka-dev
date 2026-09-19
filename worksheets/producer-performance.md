# Producer performance

Your guess before measuring: which setting moves throughput the most? ______

Which one costs something other than throughput? ______

Change one thing at a time. Every run measures for two minutes.

| Setting | Value | records/sec | Latency | Notes |
|---|---|---|---|---|
| baseline, everything from task 2 | | | | |
| `acks` | `1` instead of `all` | | | |
| `linger.ms` | `0` instead of `10` | | | |
| `batch.size` | `16384` instead of `900000` | | | |
| `compression.type` | `none` instead of `lz4` | | | |

Which setting moved the number most?

What did your guess get right, what not?
