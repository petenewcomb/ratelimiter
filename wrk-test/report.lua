-- example reporting script which demonstrates a custom
-- done() function that prints latency percentiles as CSV

done = function(summary, latency, requests)
   io.write("------------------------------\n")
   io.write(string.format("test duration (us)\t%d\n", summary.duration))
   io.write(string.format("requests allowed\t%d\n", summary.requests - summary.errors.status))
   io.write(string.format("requests denied\t%d\n", summary.errors.status))
   io.write(string.format("allow rate (per second)\t%g\n", (summary.requests - summary.errors.status) * 1000.0 * 1000.0 / summary.duration))
   io.write(string.format("deny rate (per second)\t%g\n", summary.errors.status * 1000.0 * 1000.0 / summary.duration))
   io.write(string.format("min latency (us)\t%d\n", latency.min))
   for _, p in pairs({ 1, 50, 90, 99, 99.9, 99.99, 99.999 }) do
      n = latency:percentile(p)
      io.write(string.format("p%g latency (us)\t%d\n", p, n))
   end
   io.write(string.format("max latency (us)\t%d\n", latency.max))
end
