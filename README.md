# 登录

纯JWT：无状态，所有信息都包含在 Token 中（Header token类型和签名算法，Payload 用户信息和token信息，Signature 防止token被篡改），服务端不保存任何状态（客户端->登录请求->服务器验证密码 生成JWT 返回JWT->客户端请求时带上JWT->服务器解析JWT 获取用户信息包括user ID，role，expireTime），适合完全无状态的分布式系统（因为不同的服务器都能从JWT中解析出用户，而session要靠sessionID），但无法灵活控制 Token 的失效和注销（服务器可以删除session，而token只能等过期）。 
Token + Redis：有状态，Token 存在客户端，状态保存在 Redis 中，服务端通过 Redis 管理 Token 的有效性，方便实现 Token 的失效、注销和续期，适合有状态管理需求的分布式系统。 
Session：有状态（保存了用户状态），用户信息存储在服务端（内存或 Redis 等），通过 Session ID 维护用户状态（客户端->登录请求->服务器验证密码 创建session 存储session 返回sessionID->客户端请求时带上sessionID->服务端查找session存储），适合中小型系统或传统 Web 应用，需在分布式系统中借助 Redis 共享 Session。

方案1：前端访问/code，后端创建session并返回sessionId（自动完成）给前端，它为前端发来的当前session设置属性（code），前端填写LoginForm并访问/login，后端从表单中获取用户填写的code，然后验证是否与sessionId对应的session中设置的属性（code）一致，若通过则在数据库根据phone搜索/创建用户，然后为session设置属性（user）。当用户后续发起请求时，依然根据cookie-session-sessionId，服务端就会从session存储中查找是否存在这个用户了，也可以通过ThreadLocal保存所有用户。

方案2：前端访问/code，后端在reidis中存入{phone:code}，前端通过表单访问/login时，在redis中进行验证，以及在数据库中根据phone查询和创建用户，生成token（UUID），将用户信息存入redis{tokenKey:user}，并返回给前端token

在第一层拦截器中，会根据token查询redis用户，保存到threadlocal中并刷新token有效期，它不会拦截任何请求，若没有查到用户则直接放到第二层拦截器而已。
在第二层拦截器中，会查询用户是否存在，不存在则进行拦截。

双层拦截器：第一层负责刷新token，第二层负责拦截请求。接收到请求时，token为空或redis查不到token（tokenKey），则直接放到第二层，放行路径如/login能通过，而非放行路径由于没登录就被拦截；redis查得到token则更新token的过期时间，并且将用户保存到ThreadLocal中，这样第二层就不需要重复查询Redis或其他数据源，直接通过ThreadLocal就能快速验证是否已登录。（对于其他业务请求也可以通过ThreadLocal快速获取当前用户）

为什么不写一层，给code和login放行，然后其他路径下token或tokenkey为空则拦截，否则更新token?  因为放行策略不同，若写到一层，对于放行的请求就不会经过拦截器，就不能更新token了，假设浏览商品主页不需要拦截，对于登录的用户，浏览就不会更新token了，但我们应该为所有操作进行token更新

![03058c1d21ba8e8b1fb717d1196decfb](demo.assets/03058c1d21ba8e8b1fb717d1196decfb.png)

![image-20230706203313781](demo.assets/d1bfc49052e12cfc0e61acec371c24af.png)

![image-20230706214022744](demo.assets/d1a36c76bcc789db5329032535879b52.png)

# 缓存

## 一致性设计

Cache Aside模式：查数据时先查缓存，缓存没有再查数据库；更新数据时，先更新数据库再删除缓存；删除数据时同时删除数据库和缓存
查询路径：请求 -> Caffeine -> Redis -> DB
更新路径：更新DB -> 删除Redis缓存 -> 删除当前节点本地缓存

更新数据后删缓存而不是更新缓存：1.写缓存失败怎么办 2.多个并发写谁覆盖谁 3.缓存结构可能和数据库结构不完全一致，有些缓存是聚合结果，不是单表一条数据，缓存未必知道该怎么精确改，更新成本也高
而“删缓存”就简单很多，只要删掉下次读时自然从数据库加载最新值。所以，删除缓存是让缓存失效，更新缓存是让你自己维护副本内容，后者明显更难。删除缓存更适合读多写少的场景，最合理的做法往往不是“努力把缓存写对”，而是“把旧缓存干掉，让后续读自动重建”。

采用先更新DB后删除缓存：因为“先删缓存再更新DB” 更容易把旧数据重新写回缓存，先删了缓存，还没更新数据库->读请求进来发现缓存没了->去数据库读到旧值,把旧值写回缓存->最终更新数据库，但缓存已经脏了。而先更新DB再删缓存也有时间窗口，包括并发读请求在删缓存前读到缓存脏数据、并发读请求前往更新数据库获取到数据并写回缓存（此时缓存中出现短暂旧值），但他至少保证缓存失效以后，后续回源读到的是新数据。

延时双删：延时双删的设计思想就是降低这种问题发生的概率，它的流程是先删除缓存，再更新数据库，休眠一小段时间后再删一次缓存，所以叫“双删”。但它有致命问题，1. 延时时间不好定太短，旧缓存可能还没来得及回填，定太长又增加写请求耗时或异步复杂度 2.第二次删除也可能失败如果第二次删缓存失败，还是会有不一致 3.本质上还是概率性方案它不能像事务那样保证绝对一致。 不过，由于它成本低、实现简单、效果足够好，在中小规模系统中是一种性价比较高的方案。

因此本项目采用先更新DB后删除缓存，并且为了进一步增强一致性，通过消息队列与 TTL 兜底机制保障数据库与缓存的最终一致性。消息队列有可能会将新缓存也删掉，但这是可接受的，等待后续查请求再次构建缓存即可，宁可多删一次，也不要让就缓存残留，如果担心持续删除新缓存，可以在消息里带上版本号或更新时间，在删除缓存前进行检查。

更进阶的方案是通过Cancal监听binlog，由Canal 解析出“哪条数据变了”，然后发送消息（MQ / 回调）、删除缓存。Canal本质是伪装成 MySQL 从库，订阅 binlog，从而实时拿到数据变更，它不依赖业务代码 、不会漏删缓存 、DB 是“唯一数据源” ，非常适合复杂系统 / 多服务写库

## 可靠性设计

**决定哪些数据放缓存**：判断依据是三个维度，读多写少、数据量可控、允许短暂不一致。
商城里符合条件的数据典型有商品详情（读极多、改价格不频繁）、商品分类树（几乎不变）、店铺信息、首页推荐列表、活动信息。不适合放缓存的是：订单状态（强一致要求）、支付流水、库存实时数量（秒杀另说）、用户账户余额

![image-20260324102005143](demo.assets/image-20260324102005143.png)

**选型的核心判断逻辑**

光知道"这个数据有击穿风险"还不够，还要知道为什么选这个方案而不是另一个，这才是面试真正考的。

**穿透的选型**：看 key 空间大不大。店铺 ID、商品 ID 是有界集合，用空值缓存够了。搜索词是无界的，随机枚举可以打爆空值缓存，必须上布隆过滤器。

**击穿的选型**：看能不能接受旧数据。商品详情允许短暂展示旧价格，用逻辑过期，响应不阻塞；库存和价格强一致，用互斥锁，宁可让用户等一下也要保证数据准确。
互斥锁（Mutex）是强一致的，key 过期后只有一个线程重建缓存，其他线程等待。优点是数据一定是最新的，缺点是等待期间接口会有延迟毛刺，适合**对数据实时性要求高**的场景，比如库存、价格。
逻辑过期是最终一致的，key 永不真正过期，过期标记只写在 value 里，发现逻辑过期后异步重建，请求直接返回旧数据。优点是完全无等待，缺点是窗口期内数据是旧的，适合**对响应时间敏感、能容忍短暂旧数据**的场景，比如商品详情、活动信息。

**雪崩的选型**：看是哪种雪崩。集中过期导致的，随机 TTL 打散就能解决，成本最低。Redis 宕机导致的，要靠熔断降级兜底，返回默认数据或提示稍后重试。全站共享的超热 key（分类树、首页配置），直接不设 TTL，改成写操作触发主动删除，这比随机 TTL 更可控。

很多人以为击穿和雪崩是两个独立问题，其实商城里经常同时发生。比如首页推荐列表：如果所有用户看同一份数据，key 只有一个，过期时是击穿（单个 key 并发涌入）；如果有多个城市维度的推荐 key 同时过期，是雪崩（批量 key 同时失效）。这两个场景要叠加防御，不是二选一。

## redis

 固定 TTL、随机 TTL、逻辑过期、永不过期、直接回源、互斥重建、是否缓存空值、是否启用本地缓存、锁前缀

布隆过滤器

```java
RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(RedisConstants.BLOOM_SHOP_KEY);
bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);//预期放入元素个数和误判率决定布隆过滤器的数组大小m和哈希函数个数k；这里设置10万、1% 比较省空间和时间
```

设置逻辑过期：将逻辑过期时间写到redisData中，存到redis的时候不设置过期时间



预热 + 逻辑过期 + 异步重建方案解决缓存击穿，针对缓存穿透采用空值缓存进行拦截。具体流程是：

  - 如果缓存存在且为空值，直接返回 null（缓存空值）
  - 如果缓存存在且为非空值，判断逻辑过期时间：
    - 如果未过期，直接返回缓存数据
    - 如果已过期，则返回旧数据，同时异步触发缓存重建，写回时加上逻辑过期
  - 如果缓存不存在，说明预热没做好，返回null（降级）

对于互斥锁重建方案，例如秒杀券列表查询，流程是：

  - 缓存命中则直接返回
  - 缓存未命中时，通过互斥锁控制只有一个线程查询数据库并重建缓存
  - 其他线程获取锁失败后，通过短暂休眠加重试的方式再次查询缓存
  - 为了避免重复查库，拿到锁的线程在真正查询数据库前，还需要再次检查缓存是否已经被其他线程写入，这就是所谓的双重检查
  - 锁持有时长 = 查库时间 + 写缓存时间，休眠时间应该为锁持有时长的 1/2 到 1 倍，比如查库 + 写缓存预估500ms，休眠设 300ms 比较合理。设太短会导致大量无效重试空转，锁还没释放就又来一波；设太长接口响应时间直接崩，用户体验很差。
  - 重试次数为 3 次是常见选择，理由是：
    - 第 1 次重试：大概率锁还没释放，再等一下
    - 第 2 次重试：正常情况下缓存应该已经重建好了
    - 第 3 次重试：兜底，如果还没好说明出了问题
  - 重试还没拿到：返回降级数据——比如返回一个简化版的商品信息，或者"数据加载中"的默认展示，适合商品详情这类体验优先的场景；直接抛异常/返回错误——适合强依赖这个数据的场景，比如结算页必须拿到最新价格，给错数据比报错更危险



存储object并设置随机过期时间解决缓存雪崩：在存储时在原本的过期时间上加上一个随机时间。
缓存雪崩时，重点不是让每个请求都成功，而是先保护下游数据库。通常会先通过随机 TTL、预热和多级缓存降低同时过期概率；如果仍然发生大面积失效，就要对回源流量做限流、隔离和熔断。当发现数据库响应时间和失败率明显恶化时，直接停止大规模回源，转而返回旧缓存、默认值或系统繁忙提示，用降级来换系统整体可用性。

缓存穿透问题：布隆过滤器/缓存空值

![image-20230708124239432](demo.assets/183fbe1787d3c03ca006ddd44582ed4b.png)

![image-20230708131259920](demo.assets/42c507a316111a641b9bac8b3d39be22.png)

缓存击穿（热点key）问题：互斥锁/逻辑过期



![image-20230708133530352](demo.assets/28f825235b89a7c8b7fbd13ae48b8a42.png)

![image-20230708135554065](demo.assets/15f4fb00f5292185f879211c531e90ad.png)

![image-20230708135549507](demo.assets/de4b7377f4f053ab62879786b8a920c3.png)

## caffeine

caffeine的方法包括：`contains(key)`：判断本地是否有缓存，`isNullMarker(key)`：判断本地缓存的是不是空值标记，`get(key, type)`：取本地对象，`put(key, value)`：写本地对象，`putNull(key)`：写本地空值，`evict(key)`：删除本地缓存

NULL_MARKER的作用：用于解决缓存穿透的问题，类似redis如果获取不到key可以缓存空值”“，使用专门的本地静态常量是这样语义更好。

```java
private final Cache<String, Object> cache = Caffeine.newBuilder()
            .maximumSize(10_000)//最多缓存10000个对象，旨在当前JVM进程有效
            .expireAfterWrite(Duration.ofSeconds(30))//过期时间固定30s
            .build();
```

缓存穿透

```java
if (localCacheClient.contains(key)) {
    if (localCacheClient.isNullMarker(key)) {
        return null;
    }
    return localCacheClient.get(key, type);
}
```

查询请求->local命中则返回->redis get key命中则local添加缓存（包括空值和非空值），redis是空值则返回null，非空值时检查过期时间，未过期则异步查询数据库并返回旧数据，过期则同步查询数据库并返回->查询数据库时先获取锁，若查到数据为空则为redis和local设置空值，若非空则设置redis和local的值（添加值或刷新有效期）

# 秒杀

## 概述

基本需求：
1.秒杀需要保证 不能超卖、一个人对一个优惠券最多只能下一个单。
2.当抢到秒杀券之后需要在30min内进行支付，否则要取消订单。

流程：
秒杀券缓存预热->用户发起秒杀请求->缓存验证秒杀资格（不超卖，一人一单）->异步创建订单->梯度定时检查订单支付状态->完成订单或取消订单

技术架构：
redis预热、redis+lua初步验证秒杀资格、rabbitmq异步创建订单（数据库兜底秒杀资格）、rabbitmq延时队列梯度定时检查支付状态

## 表字段设计

包括了三张表，优惠券表（主要字段为id，shopId），秒杀表（主要字段为id，voucherid，stock），优惠券订单表（主要字段为id，voucherId，userId，status，check_index，cancel_time，这里的cancel_time是预计取消时间，即订单创建时间+30min，用来定时补偿的，而不是真的指取消了）

由于秒杀过程要在redis中判断数据库中是否存在相同的orderId，因此要在redis中生成自增ID，而且Redis 做原子自增非常轻量，吞吐通常更高，也更适合分布式部署（多个数据库服务实例可能同时生成id，需要处理竞争，如果以后数据库进行分库分表，每个库各自递增，所以需要需要设置步长、偏移，维护复杂），另外，很多项目生成的不是单纯自增整数，而是类似时间戳 + 业务前缀 + 每日序列号，这种规则用 Redis 很容易实现。

 采用秒级时间戳+32位序列号count, 标准雪花算法用 毫秒时间戳 + 10 位机器 ID + 12 位序列号。
count含义: 每次调用 nextId()，在nextId()中使用Redis的increment方法为 “某个业务类型 + 某一天” 生成一个递增的整数
如果多个服务实例同时调用 nextId()，都连同一个 Redis，那没问题（Redis 保证 INCR 原子性），但如果每个服务实例连自己的 Redis（或 Redis 分片），不同实例可能生成相同 ID！此时机器ID是必要的。
此外，如果系统时间被调回（如 NTP 同步），timestamp 变小，可能生成重复 ID，正规雪花算法会处理时间回拨

## Lua脚本设计

**seckill.lua**

一开始通过redission抢分布式锁setnx: setIfAbsent{userId+voucherId:uuID+threadID}，锁存在期间只有一个线程可以尝试下单，这样可以减少高并发带来的压力（它不能彻底保证一人一单，最重要的是还是减少重复下单的并发压力），再通过数据库主键orderId（即使使用分布式ID递增但也可能出问题，一定要在数据库兜底）和唯一索引user_id+voucher_id进行兜底一人一单，即在数据库层面通过CAS update（原子性确认此前没下单且库存大于0）进行更新。这里说兜底是因为redis不是强一致性的，消息队列存在各种消息相关问题：
1.缓存和DB不是强一致性，可能会因为锁过期（为了防止下单失败且锁释放失败，锁一定要设置为可过期）、误释放、误配置、跨服务调用遗漏、redis宕机重启、运维删缓存、数据迁移、主从切换、恢复脚本、Redis异常放行、没走锁路径、集群环境细节问题等，导致redis和DB状态不一致。
2.mq多消费者,重复消费,重发投递,失败重试,重启恢复

而使用redisson锁的方案存在几个严重的问题：
1.redisson锁只能解决用户重复带来的数据库压力问题，而没有利用库存不足来减少数据库压力，而库存不足才是最重要的缓存拦截手段。若在获取锁之前先判断redis库存，在获取锁之后要减少redis库存，这样的分多次请求 一方面有可能所有请求都先通过了redis库存判断，再去获取锁和减少库存，就达不到库存不足拦截的效果，另一方面多次请求会经过多次RTT导致响应时间和开销增加
2.redisson直接开销涉及setnx/hash/lua解锁,续期watchdch,重试或快速返回, 当请求发生后Web线程一直在被占用, 锁竞争一直在发生, 线程切换,JVM抢锁,应用层等待锁等等,性能消耗更大

因此使用lua脚本，利用lua脚本原子性+redis单线程执行实现利用库存不足和重复下单两个维度拦截请求，减少数据库压力

## 秒杀预热

`@EventListener(ApplicationReadyEvent.class)` 是 Spring Boot 中的一个注解，用于监听应用上下文启动完毕并准备好提供服务（Ready）的事件。被该注解标注的方法会在应用完全启动后自动执行，常用于服务注册、缓存预热、定时任务启动等操作。

```java
@Service
@Slf4j
public class SeckillWarmUpService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        for (SeckillVoucher voucher : vouchers) {
            stringRedisTemplate.opsForValue().set(
                    RedisConstants.SECKILL_STOCK_KEY + voucher.getVoucherId(),
                    String.valueOf(voucher.getStock())
            );
        }

        Map<Long, List<String>> userIdsByVoucher = voucherOrderService.list().stream()
                .collect(Collectors.groupingBy(
                        VoucherOrder::getVoucherId,
                        Collectors.mapping(order -> String.valueOf(order.getUserId()), Collectors.toList())
                ));
        for (Map.Entry<Long, List<String>> entry : userIdsByVoucher.entrySet()) {
            String key = RedisConstants.SECKILL_ORDER_USER_SET_KEY + entry.getKey();
            stringRedisTemplate.delete(key);
            if (!entry.getValue().isEmpty()) {
                stringRedisTemplate.opsForSet().add(key, entry.getValue().toArray(String[]::new));
            }
        }
        log.info("Seckill warm-up finished, voucherCount={}, orderSetCount={}", vouchers.size(), userIdsByVoucher.size());
    }
}
```



## 异步秒杀设计

为什么采用异步秒杀：异步调用无需等待性能好(直接先给用户返回成功,再慢慢创建订单)，耦合度低扩展性强，故障隔离下游服务不影响上游服务，缓存消息流量削峰填谷。
异步会引入最终一致性问题,比如消费者挂了 / DB 挂了 / 事务失败了, 必须考虑 消费失败重试 ,死信队列 / pending-list 处理 ,状态补偿, 查询结果回显方式（MQ通常返回的是“已受理/排队中”，最终结果要再查）

消息队列天然要考虑 至少一次投递, 重试, 消费者宕机后恢复, ack 丢失 , 重复消息, 所以需要采用幂等兜底

演进过程：JVM内队列 → Redis内存队列 → Redis广播 → Redis可持久化消息流 → 专业MQ
**BlockingQueue**支持线程安全、阻塞获取和阻塞放入，但它没有解决可靠性和分布式（只在当前 JVM 内有效）
**Redis List**是队列，能模拟分布式队列，一条消息通常给一个消费者（功能单一），但它没有解决可靠性
**Redis Pub/Sub** 是发布订阅模型，能将消息广播给所有订阅者（功能单一），发布者往 channel 发消息，订阅者实时收到，但也没有解决可靠性
**Redis Streams** 支持消息追加、消息 ID、消费组、`XREADGROUP` 消费等机制。它开始具备 MQ 的关键能力，包括消息有唯一 ID（保证消息只消费一次），可以持久保留（不是弹出即消失），支持消费者组，一个组内多消费者分摊消费，可以跟踪 pending 消息（消息给消费者拿走之后会放入Pending Enrties List中，消息在返回确认之前处于 pending 状态，Redis 会记住它属于哪个 consumer、多久没处理、被投递过几次），宕机恢复时可以重新处理未确认消息，但复杂重试、死信、路由、延迟队列生态不如专业 MQ 完整，运维和排障思路也和专业 MQ 不完全一样，用不好会有 pending 累积、stream 膨胀问题
**RabbitMQ**交换机路由模型丰富,可靠性更强（ack 机制成熟,死信队列、延迟、重试链路更完善）,削峰填谷能力更标准,而且更适合业务系统间解耦）

在异步的具体实现上，MQ不需要像redis一样手写创建线程池+run方法，生产者发消息到 RabbitMQ，消费者用 `@RabbitListener` 监听队列，而真正的消费线程由 Spring AMQP 的 listener container 管理，可以自己配置监听容器的并发数

## 数据结构设计

秒杀入口极高并发，用户线程里不应该先写 DB 再发 MQ，所以这里更适合：Redis 预检成功 -> 直接投 MQ ->发送失败时做 Redis 补偿 -> 消费端做最终落库

ID生成建议：订单 ID 不要在消费端生成，应该在**秒杀入口就生成**。 这样有几个好处：

1. 用户下单成功后可以立刻拿到订单号
2. MQ 消息幂等更容易做
3. 支付、查询、取消都围绕同一个 orderId

## MQ拓扑设计

订单创建order.event.exchange->(key voucher.order.create)voucher.order.create.queue

支付检查是延迟交换机order.delay.exchange->（voucher.order.pay.check）voucher.order.pay.check.queue

死信队列是所有的订单创建和支付检查的消费者在出现非业务异常（如入队列消息堆满）时回复

**订单创建**

- Exchange: `order.event.exchange`
- Queue: `voucher.order.create.queue`
- RoutingKey: `voucher.order.create`

**支付检查**

推荐用 **RabbitMQ delayed exchange 插件**,插件实现了一种能发送延迟消息的交换机，只要在消息头中设置延迟时间即可
 因为你要的是**梯度时间**，插件方式最自然。

- Exchange: `order.delay.exchange`
- Queue: `voucher.order.pay.check.queue`
- RoutingKey: `voucher.order.pay.check`

**死信队列**

- Queue: `voucher.order.dlq`



## 消息体设计

```java
public class OrderPayCheckMessage implements Serializable {
    private String msgId;
    private Long orderId;
    private Integer checkIndex;   // 第几次检查
}
public class VoucherOrderCreateMessage implements Serializable {
    private String msgId;      // 消息唯一ID
    private Long orderId;      // 订单ID，秒杀入口生成
    private Long userId;
    private Long voucherId;
}
```

## 生产者可靠投递

重试机制：应对网络波动出现客户端连接MQ失败, 重试是阻塞式的, 因此需要合理配置时长, 性能有要求时建议禁用, 或采用异步线程去发送消息
确认机制： confirm 确保消息成功发送到交换机，无论是否成功路由到队列，都会发送 ACK，否则发送NACK，returns确保消息从交换机成功路由到了队列，若失败（如路由失败）则将原消息返回给生产者

1.编写生产者confirm和return的callback，传消息时传入correlationData(msgId)，在callback方法中进行log

`publisher-confirm-type: correlated`

比 `simple` 更适合企业项目，因为你可以把**消息唯一 ID**绑定到 `CorrelationData`，方便确认是哪条消息投递成功/失败，在投递消息会指定（exchange,routing key,object,message,correlationData)
object是业务需要使用的信息，它只代表消息体内容，如 自定的消息id,订单id,userid，一般单独封装为一个自定义业务消息类如VoucherOrderCreateMessage，通常消息id设置为一个**UUID**，虽然并不绝对唯一但重复概率非常小，且在MQ中可以**结合消息幂**等
message是完整的消息，它的内容是object，它的消息头也需要设置一个id字段，一般让它直接拿object的id
correlationData是MQ向生产者发送确认时用的，这个Data也要设置一个唯一id，一般也让它直接拿object的id

`convertAndSend + try/catch` 捕获的是“同步发送阶段”的异常，而 `returns` / `confirm` 回调处理的是“Broker 异步反馈”的结果。 这两类不是同一条链路，也不一定同时发生。

`convertAndSend()` 本身会抛 `AmqpException`，所以如果出现这些“发送当下”的问题，连接拿不到、channel 创建失败、broker 暂时不可用、消息转换异常，那就会抛出异常，但如果设置了重试机制则暂时就会进行重试，直到重试耗尽都还是出现这些问题时再抛出异常，那么就会被catch所捕获。

而`returns` 回调针对的是：消息成功到达 exchange但是 没有任何 queue 能路由到。这时 Broker 会把消息 return 给生产者，Spring 通过 `ReturnsCallback` 异步通知你。这种情况下 `convertAndSend()` 往往不会抛异常。

同理，confirms回调也是一个异步的过程，在消息成功到达exchange之后Broker就会触发回调机制。但confirm也可以设置成同步的，即simple，而设置回调最主要的原因是可以它可以在回调数据包中传送成功消息的ID

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated #开启回调 可以设置confirm回调行为
    publisher-returns: true # 开启回调 可以设置returns回调行为
    connection-timeout: 1s # MQ连接超时时间
    template:
      mandatory: true	# 若消息路由失败则触发returns回调，重试做的是在调用 convertAndSend() 时，失败就在客户端重试发
      retry:
        enabled: true # 开启超时重试机制 
        initial-interval: 1000ms #失败后的初始等待时间
        multiplier: 1 #失败后下次的等待时长倍数,下次等待时长=当前失败等待时长*倍数
        max-attempts: 3 #最大重试次数
```

```java
@Configuration
@Slf4j
public class RabbitTemplateConfig {

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter);
        rabbitTemplate.setMandatory(true);

        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            String id = correlationData != null ? correlationData.getId() : null;
            if (ack) {
                log.info("MQ消息投递到Exchange成功, msgId={}", id);
            } else {
                log.error("MQ消息投递到Exchange失败, msgId={}, cause={}", id, cause);
            }
        });

        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("MQ消息从Exchange路由到Queue失败, exchange={}, routingKey={}, replyText={}, msg={}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyText(),
                    new String(returned.getMessage().getBody(), StandardCharsets.UTF_8));
        });

        return rabbitTemplate;
    }
}
```

2.封装一个MQ发送器，这样可以统一设置 `CorrelationData` 和消息属性。

```java
@Component
@RequiredArgsConstructor
public class ReliableMessageSender {

    private final RabbitTemplate rabbitTemplate;

    public void sendOrderCreate(VoucherOrderCreateMessage msg) {
        CorrelationData correlationData = new CorrelationData(msg.getMsgId());
        rabbitTemplate.convertAndSend(
                MQConfig.ORDER_EVENT_EXCHANGE,
                MQConfig.ORDER_CREATE_ROUTING_KEY,
                msg,
                message -> {
                    message.getMessageProperties().setMessageId(msg.getMsgId());
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                },
                correlationData
        );
    }

    public void sendPayCheck(OrderPayCheckMessage msg, long delayMillis) {
        CorrelationData correlationData = new CorrelationData(msg.getMsgId());
        rabbitTemplate.convertAndSend(
                MQConfig.ORDER_DELAY_EXCHANGE,
                MQConfig.ORDER_PAY_CHECK_ROUTING_KEY,
                msg,
                message -> {
                    message.getMessageProperties().setMessageId(msg.getMsgId());
                    message.getMessageProperties().setHeader("x-delay", delayMillis);
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                },
                correlationData
        );
    }
}
```

## MQ可靠性

问题:1.消息在MQ丢失(MQ宕机,还没持久化成功,队列或交换机不是durable,消息不是persistent,没有confirm机制) 2.消息挤压导致MQ阻塞(消息堆积时,如消息处理不及时,内存被占满时执行阻塞的pageOut将内容转移到磁盘,等转移结束后才可以接收新消息并进行处理)

方案1:持久化,包括交换机 队列 消息 三个方面的持久化, 消息进入内存的同时会异步或同步地写入磁盘的持久化日志中,正常情况下，消息依然会优先保存在内存中以实现快速投递，只有在内存压力大时才会转移（Evict）到磁盘message.setDeiveryMode(MessageDeliveryMode.PERSISTENT)

方案2:lazyQueue, 直接将消息存到磁盘(进行了I/O优化),需要消费时从磁盘读取,瞬时吞吐量较低，但其优点是性能非常平稳

不过自3.12起,classic queue整体行为已经接近lazy queue了,持久化的本质是让交换机、队列和消息具备 Broker 重启后的恢复能力；现代 RabbitMQ 会积极将数据写入磁盘，在内存中只保留较小的工作集

```java
rabbitTemplate.convertAndSend(
                MQConfig.ORDER_EVENT_EXCHANGE,
                MQConfig.ORDER_CREATE_ROUTING_KEY,
                msg,
                message -> {
                    message.getMessageProperties().setMessageId(msg.getMsgId());
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);//持久化message
                    return message;
                },
                correlationData
        );

return ExchangeBuilder.directExchange(ORDER_EVENT_EXCHANGE)
                .durable(true) //持久化exchange
                .build();
return QueueBuilder.durable(ORDER_CREATE_QUEUE)//持久化queue
                .deadLetterExchange(ORDER_DLX_EXCHANGE)
                .deadLetterRoutingKey(ORDER_DLX_ROUTING_KEY)
                .build();
```

## 消费者可靠接收

消费者会出现消费异常,宕机等问题,只有当消费者向MQ返回确认时,MQ才会删除消息,否则会不断重发直到返回ACK或REJECT,或者达到最大重试次数

```yaml
listener: # consumer config
  simple:
    prefetch: 10
    acknowledge-mode: manual # none,manual,auto(business ok ->ack ; business exception ->nack ; message exception-> reject)
    retry:
      enabled: false
```

acknowledge-mode: manual 企业里一般更倾向**手动 ack**，原因：

- 业务成功后再 ack，控制权更强
- 可区分：
  - 可重试异常 → `basicNack(requeue=false)` 进死信或重投
  - 不可重试异常 → 直接拒绝
- 能更好实现**幂等 + 重试 + 死信治理**

`retry: enabled: false`

把失败重试机制（以及重试最大次数后失败放到失败交换机）取消掉，不建议监听器自带 retry，Spring AMQP 的 listener retry 很容易让业务异常在容器层被重复消费，和你自己的幂等、死信策略混在一起，复杂且不透明。
 更主流的做法是：

- **消费者自己捕获异常**
- 明确区分是否重投 / 是否入死信
- 保证业务幂等

## 秒杀入口实现

Redis资格预检和库存预扣->发送消息到order交换机->消息发送失败时还会进行redis补偿

不过这种方案只能对可catch的异常进行redis补偿，对于捕获不到的异常，比如消息成功到达exchange但没办法路由出去，这个错误可以通过returns机制进行人工补偿redis，这也是需要设置回调的重要性。
但如果消息异常丢失了，就只能浪费redis的库存了，不过还在可接受的范围之内。

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherOrderServiceImpl implements VoucherOrderService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ReliableMessageSender reliableMessageSender;
    private final RedisIdWorker redisIdWorker;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("voucher:order");
        String msgId = UUID.randomUUID().toString();

        String stockKey = "seckill:stock:" + voucherId;
        String orderUserSetKey = "seckill:order:user:" + voucherId;

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Arrays.asList(stockKey, orderUserSetKey),
                userId.toString()
        );

        if (result == null) {
            return Result.fail("系统繁忙，请重试");
        }
        if (result == 1) {
            return Result.fail("库存不足");
        }
        if (result == 2) {
            return Result.fail("不可重复下单");
        }

        VoucherOrderCreateMessage message = new VoucherOrderCreateMessage(
                msgId, orderId, userId, voucherId, LocalDateTime.now()
        );

        try {
            reliableMessageSender.sendOrderCreate(message);
        } catch (Exception e) {
            log.error("发送创建订单消息失败, 进行Redis补偿, msg={}", message, e);
            rollbackRedisPreDeduct(voucherId, userId);
            return Result.fail("下单人数过多，请稍后再试");
        }

        return Result.ok(orderId);
    }

    private void rollbackRedisPreDeduct(Long voucherId, Long userId) {
        String stockKey = "seckill:stock:" + voucherId;
        String orderUserSetKey = "seckill:order:user:" + voucherId;
        stringRedisTemplate.opsForValue().increment(stockKey);
        stringRedisTemplate.opsForSet().remove(orderUserSetKey, userId.toString());
    }
}
```

## 创建订单

**创建消费者Listener**

**消费者创建订单时必须保证**：

1. 幂等，这里创建两层，第一层订单号（主键）唯一，第二层用户+优惠券唯一索引

   ```sql
   ALTER TABLE voucher_order
   ADD CONSTRAINT uk_user_voucher UNIQUE (user_id, voucher_id);
   ```

2. DB 扣库存成功后再创建订单

3. 创建成功后发首条支付检查消息

4. 异常时手动 nack / reject （通过channel）

5. 如果 DB 扣减失败，要补偿 Redis 

**消费者具体实现**： 尝试通过voucherOrderService创建订单channel.basicAck(message.getMessageProperties().getDeliveryTag())，否则捕获异常，业务异常channel.basicReject，其他异常指定不进行重试channel.basicNack，这些异常都会进入死信队列

**voucherOrderService创建订单实现**

proxy执行事务扣库存和创建订单 + 发送消息

事务方法：
1.检查orderId唯一（主键查的更快）和 userId+voucherId唯一，失败则redis补偿，返回
2.扣减库存，失败则redis补偿，抛出业务异常
3.创建订单

## 支付回调

支付端要保证支付时处于支付时间内，支付成功后要进行回调，修改订单状态为已支付，注意两个点

1. 保证幂等：通过paymentservice.paySuccess(Long orderId)更新状态为2，这样即使支付回调重复也不会重复更新
2. 支付回调和关单的并发问题：假设超时关单前用户正好完成支付，则会出现关单和支付回调同时进行，谁先提交谁覆盖。因此使用redssion锁使一个时间只有一个线程能进入，并且通过状态机兜底。 redisson锁只能减少冲突概率（只放一个进来就可以了），但锁可能会出现各种问题，并不能实现最终一致性，还要靠数据库条件更新的幂等性以及最终一致性的兜底。

**状态机控制**（比条件更新更“高级一层”）

状态机 = 明确规定“状态之间能不能转”

订单状态：
UNPAID → PAID
UNPAID → CLOSED
PAID   → REFUND

核心点不是“能不能更新”，而是“这个状态 → 下一个状态“是否合法

## 支付检查

延迟消息到了：查订单状态->若已支付或已取消，结束-> 若未支付，获取下一次梯度时间和订单超时时间，若订单已经超时了或没有梯度时间了，则取消订单并恢复库存->否则再投递一条延迟检查消息

redis资格预检和库存预扣->发送消息到order交换机->消息发送失败时还会进行redis补偿
不过这种方案只能对可catch的异常进行redis补偿，对于捕获不到的异常，比如消息成功到达exchange但没办法路由出去，这个错误可以通过returns机制进行人工补偿redis，这也是需要设置回调的重要性。但如果消息异常丢失了，就只能浪费redis的库存了，不过订单还没创建，这样的损失还在可接受的范围之内。

由于创建订单时使用事务，若创建订单和扣减库存都成功，此时redis和DB一致，然后发送支付检查消息失败，此时回滚redis和数据库所有操作的消耗是巨大的，因此需要进行try catch，防止DB订单和库存回滚，在catch中也不需要进行redis回滚操作，我们需要做的等待异步扫描进行重发。此外，如果首条支付检查消息发送成功，但是消息异常丢失了，又或者在梯度检查的后续过程中消息异常丢失了，若不进行重发，将导致最后没办法超时关单，甚至还有可能在执行关闭库存的时候服务器宕机，导致库存无法释放，这个代价是巨大的，因此支付检查消息一定需要实现消息重发，让超时的订单顺利关闭，恢复库存。通过消息幂等性让重发不会导致错误。

方案一：异步任务定期扫描订单表，补发待支付但却超出了最后支付时间的消息，补发的消息会进入检查逻辑，取消订单和释放库存，因此在判断逻辑中不仅仅要检查梯度，还要检查最晚支付时间。补发有可能会导致支付检查产生时间差，即支付检查的时间比最晚支付时间还长，但是没有关系，因为检查支付是后发的，库存可以晚一点恢复， 只要保证在支付端支付时时间是在最晚支付时间内就可以了。

方案二：设置outbox表进行记录，粒度是消息，异步任务扫描，针对梯度中的每次可能的消息丢失进行补发。

方案三：通过死信进行审核和重发

本项目采用方案一。

**支付检查消费者具体实现**：尝试通过orderPayCheckService.handlePayCheck(msg)创建订单channel.basicAck(message.getMessageProperties().getDeliveryTag())，否则捕获异常，业务异常channel.basicReject，其他异常指定不进行重试channel.basicNack，这些异常都会进入死信队列

**orderPayCheckService检查支付具体实现**：
handlePayCheck方法：查询数据库未支付订单，若订单不存在则退出，若存在则检查是否当前时间大于订单的最晚支付时间或checkIndex已达上限，如果是则取消订单，否则继续发消息，延迟时间是PAY_CHECK_DELAY_MILLIS.get(nextIndex)

cancelOrder事务方法：1.加redisson锁 2.获取锁失败说明有其他线程在进行操作，如支付回调，为避免冲突直接离开，即使进到cancelOrder的线程失败了，也可由定时任务重新补发消息进行取消 3.数据库兜底条件（未支付）更新订单状态为取消，说兜底是因为有可能出现并发问题，拿了锁也不一定靠谱，导致已支付订单被误取消，还有幂等防止重复处理 4.若成功更新了订单状态，则继续更新库存+1，给redis做补偿

# 测试

## 功能测试

1.登录模块所有接口正常,  包括/user/code, /user/login

2.缓存模块所有接口正常，包括/shop/query/{id} , /shop/of/name , shop/update , shop/save , /list/{shopId}

3.秒杀模块所有接口正常，包括/vouhcer/addVoucher , /voucher/addSeckill ,  /voucher-order/seckill/{id} 

4.mysql正常,localcache正常,redis正常,mq正常

## 压力测试

关于秒杀性能的测试,这是mq中已经堆积了大量支付检查的性能结果,模拟更加真实的场景. 而超卖和一人一单的响应时间更快是因为它们是在消息没有堆积时进行的测试,只检测功能而不检测性能.

| 测试模块 | 线程数 | 库存 | rampup | loopcount | 样本 | 平均响应时间 | 异常率 | qps  | 备注 |
| -------- | ------ | ---- | ------ | --------- | ---- | ------------ | ------ | ---- | ---- |
| 超卖     | 1000   | 300  | 1      | 1         | 1000 | 81           | 0      | 951  | 正常 |
| 一人一单 | 1000   | 1000 | 1      | 5         | 5000 | 357          | 0      | 914  | 正常 |
| 秒杀性能 | 1000   | 4000 | 1      | 1         | 100  | 861          | 0      | 319  |      |
|          | 2000   | 4000 | 1      | 1         | 1000 | 1658         | 0      | 381  |      |
|          | 2000   | 4000 | 2      | 1         | 2000 | 1582         | 0      | 388  |      |

| 测试模块 | 线程数 | rampup | loopcount | 样本  | 平均响应时间 | 异常率 | qps  | 备注                   |
| -------- | ------ | ------ | --------- | ----- | ------------ | ------ | ---- | ---------------------- |
| 查询性能 | 1000   | 1      | 1         | 1000  | 3            | 0      | 672  | 开启缓存               |
|          | 1000   | 1      | 10        | 10000 | 91           | 0      | 2483 | 开启缓存（商品id随机） |
|          | 1000   | 5      | 10        | 10000 | 4            | 0      | 1782 | 开启缓存               |
|          | 1000   | 10     | 10        | 10000 | 2            | 0      | 990  | 开启缓存               |
|          |        |        |           |       |              |        |      |                        |
|          | 1000   | 1      | 1         | 100   | 460          | 0      | 254  | 未开启缓存             |
|          | 1000   | 1      | 10        | 10000 | 547          | 0      | 813  | 未开启缓存             |
|          | 1000   | 5      | 10        | 10000 | 589          | 0      | 652  | 未开启缓存             |
|          | 1000   | 10     | 10        | 10000 | 485          | 0      | 420  | 未开启缓存             |

# 其他

项目基于 **Redis     + AOP +** **自定义注解**实现多维度限流，支持全局、IP、用户三级限流，提升系统在高并发场景下的稳定性并降低恶意刷接口风险；

项目中使用了redis和mq两个中间件,因此在一致性和可靠性问题上做了大量考虑. 而至于caffeine将在未来扩展到多JVM实例时才使用通知功能进行同步,目前单实例暂且不用
