-- KEYS[1] = stockKey
-- KEYS[2] = userSetKey
-- ARGV[1] = userId

local removed = redis.call('srem', KEYS[2], ARGV[1])
if removed == 1 then
    redis.call('incr', KEYS[1])
    return 1
end
return 0