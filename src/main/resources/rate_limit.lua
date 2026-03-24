local key = KEYS[1] --KEYS[1] = 限流 key
local now = tonumber(ARGV[1]) --ARGV[1] = 当前时间戳(毫秒)
local window = tonumber(ARGV[2])--ARGV[2] = 窗口大小（毫秒）
local limit = tonumber(ARGV[3])--ARGV[3] = 最大请求数
local expire_time = now - window

--返回值：0=未超限，1=已超限

-- 移除窗口外的旧请求记录
redis.call('ZREMRANGEBYSCORE', key, 0, expire_time)
-- 统计当前窗口内请求数
local count = redis.call('ZCARD', key)
if count >= limit then
    return 1  -- 超限
end
-- 添加当前请求（以时间戳为 score 和 member 保证唯一性）
redis.call('ZADD', key, now, now .. '-' .. math.random(100000))
-- 设置 key 过期时间（窗口大小 + 1秒缓冲）
redis.call('PEXPIRE', key, window + 1000)
return 0  -- 未超限