
local userId = ARGV[1]
local stockKey = KEYS[1]
local orderKey = KEYS[2]
if (tonumber(redis.call("get", stockKey)) <= 0)then
    return 1
end
if (redis.call("sismember", orderKey, userId) == 1) then
    return 2
end
redis.call("incrby", stockKey, -1)
redis.call("sadd", orderKey, userId)
return 0