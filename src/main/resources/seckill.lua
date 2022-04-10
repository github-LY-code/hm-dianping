-- 优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]
-- 订单id
local orderId = ARGV[3]
-- 库存
local stockKey = 'seckill:stock:'..voucherId
-- 订单
local orderKey = 'seckill:order:'..voucherId

-- 判断库存
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 库存不足
    return 1
end

-- 判断用户是否下单
if (redis.call("sismember", orderKey, userId) == 1) then
    -- 重复下单
    return 2
end

-- 扣减库存
redis.call('incrby', stockKey, -1)
-- 添加订单
redis.call('sadd', orderKey, userId)
-- 发送到消息队列中
redis.call('xadd', 'stream.order', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0