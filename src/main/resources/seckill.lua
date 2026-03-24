-- KEYS[1]=stockKey , KEYS[2]= orderUserSetKey 即优惠券id, ARGV[1]=userId
-- 0:成功 1:库存不足 2:重复下单
-- seckill:stock:{voucherId}            -> 剩余库存
-- seckill:order:user:{voucherId}       -> Set，存已抢购用户ID
-- seckill:order:msg:{msgId}            -> 消息发送状态短期幂等/补偿标记
-- order:pay:lock:{orderId}             -> 支付/取消互斥锁
local stock = tonumber(redis.call('get', KEYS[1]))
if not stock or stock<=0 then
    return 1
end

if redis.call('sismember',KEYS[2],ARGV[1]) == 1 then
    return 2
end

redis.call('decr',KEYS[1])
redis.call('sadd',KEYS[2],ARGV[1])
return 0
