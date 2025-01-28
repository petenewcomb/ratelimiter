require 'report'

counter = 0

request = function()
   wrk.headers["X-Account-ID"] = "invalid" .. counter .. "@example.com"
   counter = counter + 1
   return wrk.format()
end
