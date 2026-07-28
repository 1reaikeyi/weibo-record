# Weibo-comment大众点评

基于 Spring Boot + Vue 3 的Weibo-comment大众点评，使用redis+nginx的分布式系统，提供用户认证、文章管理、分类管理、优惠券秒杀，笔记点赞评论核心功能。

------



# 后端说明



<img src="说明/原型功能/one.png" alt="封面" style="zoom:75%;" />

# 项目结构

```
weibo-comment/
├── backend-spring-weibo-comment/        # 后端代码
├── database-sql/                        #数据库脚本目录
│   ├── sql.txt                          # 数据库初始化SQL
│   └── 数据库设计文档.md                  # 完整的数据库设计说明
├── frontend-vue-weibo-comment/          # 前端代码（Vue 3）
└── 说明/                                 # 项目说明文档
      ├── 原型功能/                         # 前端原型截图
      ├── 并发测试结果/                     # 秒杀并发测试结果
      │   ├── 乐观锁解决超卖.png            # 乐观锁方案测试截图
      │   ├── 分布式锁解决集群一人多单.png    # 分布式锁方案测试截图
      │   ├── 悲观锁集群不能一人一单.png     # 悲观锁方案测试截图
      │   ├── redis同步.png         # Redis同步测试截图
      │   ├── redis同步.txt         # Redis同步测试Slf4j日志
      │   ├── redis异步.png         # Redis异步（队列）测试截图
      │   ├── redis异步.txt         # Redis异步（队列）测试Slf4j日志
      │   ├── stream异步.png        # Redis Stream异步测试截图
      │   └── stream异步.txt        # Redis Stream异步测试Slf4j日志
      ├── postman测试文档            # postman测试文档
      ├── 高并发测试文档              # 高并发测试文档
      └── 接口文档.md              # 完整的API接口文档
```

# 环境要求

- JDK 17+
- Spring Boot 3+
- Node.js 20.19.0+ 或 22.12.0+
- MySQL 8.0+
- Redis 7.0+

## 一、用户管理模块

### 需求阶段

**需求背景**：项目需要一个完整的用户系统，支持注册、登录、信息修改、头像上传等基本功能。

**痛点**：

- 传统Session认证在分布式环境下不好扩展
- 密码明文存储不安全
- 用户头像上传需要支持本地和云端（阿里云OSS）

### 设计阶段

**设计思路**：

Q：为什么不用Session而用JWT？
> A：Session需要在服务端维护会话状态，集群部署时需要Session共享（Redis），但每次请求都要查Redis。JWT是无状态的，Token本身携带用户信息，服务端只需要验证签名即可，更适合分布式架构。

Q：为什么token要用redis存储？
> | 维度         | 服务端 Session                |  Redis           |
> | ------------ | ----------------------------- | ----------------------- |
> | 部署架构     | 单体友好，集群麻烦            | 天生适配分布式、微服务  |
> | 存储位置     | 应用服务器内存                | 独立中间件 Redis        |
> | 客户端适配   | 依赖 Cookie，APP / 小程序难用 | Header 传输，全终端兼容 |
> | 服务重启影响 | 全部用户掉线                  | 不受影响                |
> | 强制下线     | 实现复杂                      | 直接删除 key，简单      |
> | 横向扩容     | 差                            | 优秀                    |
> | 跨域场景     | Cookie 跨域限制多             | 无 Cookie 限制          |

### 编码阶段

**策略流程图**：

```java
用户注册 → UserController/register() → 加密密码 → MySQL保存用户 → 返回注册成功
用户登录 → UserController/login() → 校验用户名密码 → 生成JWT Token → Redis存储Token → 返回Token
请求拦截 → 直接拦截脚本等操作LoginInterceptor/对于活跃用户刷新ReLoginInterceptor → 校验Token → 滑动过期刷新 → 放行请求
```

**部分代码**：

```java
// SecurityConfig.java - 注册BCryptPasswordEncoder为Spring Bean
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

// UserServiceImpl.java - 用户校验（BCrypt加密，单向哈希无法解密）
// matches(rawPassword, encodedPassword): 将明文与加密后的密码比对
if (!passwordEncoder.matches(password, checkUser.getPassword())) {
    throw new RuntimeException("用户名或密码错误");
}

// UserController.java - 注册时加密密码
// BCrypt内置随机盐，每次encode结果不同
user.setPassword(passwordEncoder.encode(user.getPassword()));
```

### 问题修复阶段

**问题1**：Token过期时间固定，用户活跃时Token也会过期

**修复方案**：实现滑动过期策略，在ReLoginInterceptor中每次请求时刷新Redis中Token的有效期

```java
// ReLoginInterceptor.java
@Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    String token = request.getHeader("Authorization");
    Map<String, Object> claims = JwtUtil.parseJWT(jwtProperties.getSecretKey(), token);
    String currentId = claims.get(JwtConstant.ID).toString();
    Long id = Long.parseLong(currentId);
    
    // 验证Token是否与Redis中存储的一致
    String standard_token = stringRedisTemplate.opsForValue().get("bigevent:" + id);
    if (!standard_token.equals(token)) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }
    
    // 刷新Token有效期（滑动过期策略）
    stringRedisTemplate.expire("bigevent:" + id, jwtProperties.getTtlMillis(), TimeUnit.SECONDS);
    
    // 将用户信息存入ThreadLocalContextHolder
    ThreadLocalContextHolder.set(claims);
    return true;
}
```

**问题2**：头像上传到本地后，部署到服务器路径不对

**修复方案**：集成阿里云OSS，上传到云端并返回CDN访问URL

---

## 二、文章管理模块

###  需求阶段

**需求背景**：需要支持文章的CRUD操作，文章量较大时需要缓存优化。

**痛点**：
- 文章列表查询慢
- 热点文章访问压力大
- 缓存与数据库一致性问题

###  设计阶段

**设计思路**：

Q：为什么用逻辑过期而不是物理过期？
> A：物理过期的话，缓存过期瞬间会有大量请求穿透到数据库（缓存击穿）。逻辑过期是缓存永不过期，但在数据中记录过期时间，过期后通过分布式锁让一个线程去更新缓存，其他线程返回旧数据，这样不会导致数据库压力骤增。

Q：为什么不直接用@Cacheable注解？
> A：@Cacheable是Spring提供的声明式缓存，虽然方便但不够灵活。比如需要自定义缓存策略、分布式锁控制、逻辑过期等场景，手动控制Redis操作更合适。


### 编码阶段

**策略流程图**：

```java
查询文章 → ArticleController → ArticleServiceImpl → Redis查询缓存
    ├─ 缓存存在且未过期 → 直接返回缓存数据
    ├─ 缓存存在但已过期 → RedisLock分布式锁 → 查询数据库 → 更新缓存 → 返回新数据
    └─ 缓存不存在 → 查询数据库 → 设置逻辑过期缓存 → 返回数据
更新文章 → ArticleController → ArticleServiceImpl → 更新MySQL → 删除Redis缓存
```

**部分代码**：

```java
// ArticleServiceImpl.java - 逻辑过期缓存查询
String key = KEYS + id;
String cacheJson = stringRedisTemplate.opsForValue().get(key);
if (StrUtil.isBlank(cacheJson)) {
    // 缓存不存在，查询数据库
    return getById(id);
}
RedisData redisData = JSONUtil.toBean(cacheJson, RedisData.class);
```

### 问题修复阶段

**问题**：缓存击穿问题 - 热点文章缓存过期瞬间，大量请求同时穿透到数据库

**修复方案**：使用分布式锁（RedisLock）+ 逻辑过期策略

---

## 三、分类管理模块

### 需求阶段

**需求背景**：文章需要分类管理，支持分类的增删改查，分类数据相对稳定但访问频繁。

**痛点**：
- 分类数量较少但查询频率高
- 需要与文章模块共享缓存策略
- 分类修改后需要及时同步到缓存

### 设计阶段

**设计思路**：

Q：为什么复用文章模块的缓存策略？
> A：分类数据和文章数据的缓存需求相似——都是读多写少、需要防止缓存击穿。复用相同的逻辑过期+分布式锁策略可以减少代码重复，提高可维护性。

Q：分类和文章的缓存策略有什么差异？
> A：分类数据量更小（通常几十到几百个），缓存命中率更高，可以设置更长的逻辑过期时间。而文章数据量大，需要更频繁地更新缓存。

### 编码阶段

**策略流程图**：

```java
查询分类 → CategoryController → CategoryServiceImpl → Redis查询缓存
    ├─ 缓存存在且未过期 → 直接返回缓存数据
    ├─ 缓存存在但已过期 → RedisLock分布式锁 → 查询数据库 → 更新缓存 → 返回新数据
    └─ 缓存不存在 → 查询数据库 → 设置逻辑过期缓存 → 返回数据
更新分类 → CategoryController → CategoryServiceImpl → 更新MySQL → 删除Redis缓存
```

**部分代码**：

```java
// CategoryServiceImpl.java - 更新缓存后主动删除
@Override
public Boolean updateCache(Category category) {
    String key = KEYS + category.getId();
    boolean result = super.updateById(category);
    stringRedisTemplate.delete(key);  // 更新后删除缓存，下次查询从数据库获取
    return result;
}
```

### 问题修复阶段

**问题**：分类修改后，文章页面显示的分类名称没有更新

**修复方案**：在分类更新/删除时主动删除缓存，确保下次查询时从数据库获取最新数据

```java
@Override
public Boolean updateCache(Category category) {
    String key = KEYS + category.getId();
    boolean result = super.updateById(category);
    stringRedisTemplate.delete(key);  // 删除缓存
    return result;
}
```

---

## 四、探店博文模块

### 需求阶段

**需求背景**：实现探店笔记功能，支持用户发布探店博文、点赞互动等社交功能。

**痛点**：
- 点赞操作并发冲突问题
- 点赞状态需要实时查询
- 热点笔记点赞数统计压力大

### 设计阶段

**设计思路**：

Q：为什么点赞用Redis的ZSet而不是普通Set？
> A：ZSet可以存储分数（timestamp），这样可以按点赞时间排序，方便获取热门点赞用户。同时ZSet的score操作是原子的，不会出现并发问题。

Q：为什么点赞数同时存Redis和MySQL？
> A：Redis用于实时查询和计数，MySQL用于持久化存储。点赞操作先更新MySQL再更新Redis，保证数据最终一致性。

### 编码阶段

**策略流程图**：

```java
点赞请求 → BlogController/likes() → 更新MySQL点赞数 → Redis ZSet记录点赞用户（score=timestamp）→ 返回结果
取消点赞 → BlogController/likes() → 更新MySQL点赞数 → Redis ZSet移除点赞用户 → 返回结果
查询点赞状态 → Redis ZSet/ZScore判断用户是否存在
查询热门点赞 → Redis ZSet/ZRange获取Top N用户ID → 查询用户信息 → 返回结果
```

**部分代码**：

```java
// BlogController.java - 点赞操作（Redis ZSet）
String key = "blog:liked:" + blogId;
Boolean isMember = stringRedisTemplate.opsForZSet().score(key, userId) != null;
if (Boolean.TRUE.equals(isMember)) {
    // 已点赞，取消点赞
    stringRedisTemplate.opsForZSet().remove(key, userId);
} else {
    // 未点赞，添加点赞
    stringRedisTemplate.opsForZSet().add(key, userId, System.currentTimeMillis());
}
```

### 问题修复阶段

**问题**：点赞操作在高并发下可能出现计数不准确

**修复方案**：MySQL使用原子操作 `setSql("liked= liked + 1")`，Redis使用ZSet的原子add/remove操作，保证计数一致性。

---

## 五、评论与回复模块

### 需求阶段

**需求背景**：实现评论功能，支持对探店笔记的评论和回复，支持多级回复。

**痛点**：
- 评论数据量大，查询性能要求高
- 需要支持评论的点赞和举报功能
- 评论与回复的层级关系需要清晰

### 设计阶段

**设计思路**：

Q：为什么用parent_id区分评论和回复？
> A：`parent_id = 0` 表示直接评论博文，`parent_id = 1` 表示回复其他评论。这种设计可以支持无限层级的回复，同时查询时可以通过parent_id区分评论和回复。

Q：评论表为什么需要answer_id字段？
> A：`answer_id` 记录回复目标评论的ID，用于构建评论的回复链，方便前端展示回复关系。

### 编码阶段

**策略流程图**：

```java
发表评论 → BlogCommentsController/save() → 设置parent_id=0 → MySQL保存 → 返回结果
回复评论 → BlogCommentsController/save() → 设置parent_id=1，answer_id=目标评论ID → MySQL保存 → 返回结果
查询评论列表 → BlogCommentsController/list() → MySQL按blog_id分页查询 → 返回评论列表（含回复）
点赞评论 → BlogCommentsController/likes() → MySQL更新点赞数 → 返回结果
```

**部分代码**：

```java
// BlogCommentsController.java - 回复评论（设置parent_id和answer_id）
BlogComments blogComments = BeanUtil.toBean(blogCommentsDTO, BlogComments.class);
blogComments.setParentId(1L);           // 标记为回复
blogComments.setAnswerId(targetId);     // 设置回复目标评论ID
blogCommentsService.save(blogComments);
```

### 问题修复阶段

**问题**：评论状态管理（正常、被举报、禁止查看）

**修复方案**：在blog_comments表中设置status字段，0表示正常，1表示被举报，2表示禁止查看。查询时过滤掉status=2的评论。

---

## 六、文件管理模块

### 需求阶段

**需求背景**：实现文件上传下载功能，支持本地存储和阿里云OSS云存储两种方式。

**痛点**：
- 本地存储在多实例部署时文件不一致
- 大文件上传需要分片处理
- 文件访问需要URL映射

### 设计阶段

**设计思路**：

Q：为什么提供两种文件存储方式？
> A：本地存储用于开发测试环境，简单快捷；阿里云OSS用于生产环境，支持高可用和CDN加速。

Q：文件命名为什么用UUID？
> A：UUID全局唯一，避免文件名冲突，同时增加安全性（防止文件遍历攻击）。

### 编码阶段

**策略流程图**：

```java
文件上传（本地）→ FileController/upload() → UUID生成文件名 → 保存到本地目录 → 返回本地访问URL
文件上传（阿里云OSS）→ FileOssController/upload() → UUID生成文件名 → AliOssUtil上传 → 返回CDN访问URL
文件下载（本地）→ FileController/download() → 读取本地文件 → 设置Content-Disposition → 返回文件流
文件下载（阿里云OSS）→ FileOssController/download() → AliOssUtil下载 → 返回文件流
```

**部分代码**：

```java
// FileController.java - 本地文件上传（UUID命名）
String originalFilename = file.getOriginalFilename();
String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
String fileName = UUID.randomUUID().toString() + extension;
file.transferTo(new File(basePath + fileName));
```

### 问题修复阶段

**问题**：文件下载中文文件名乱码

**修复方案**：使用URLEncoder编码文件名，同时设置Content-Disposition响应头

```java
response.setHeader("Content-Disposition", "attachment;filename=" + 
    URLEncoder.encode(fileName, StandardCharsets.UTF_8));
```

---

## 七、优惠券与秒杀模块

### 需求阶段

**需求背景**：实现优惠券秒杀功能，支持高并发场景下的库存扣减和一人一单限制。

**痛点**：
- 高并发下库存超卖问题
- 分布式环境下一人一单限制
- 锁竞争导致性能下降

### 设计阶段

**设计思路**：

Q：为什么用Redis做秒杀而不是直接操作数据库？
> A：数据库的处理能力有限（MySQL单机约1000 QPS），而Redis可以轻松处理10万+ QPS。先在Redis中完成库存扣减和订单校验，再异步写入数据库，这样可以扛住瞬时流量。

Q：为什么用Lua脚本？
> A：Lua脚本可以保证多个Redis命令的原子性执行，避免竞态条件。比如扣库存和判断一人一单必须同时成功或同时失败。

Q：为什么要异步处理订单？
> A：如果同步处理，用户下单请求需要等待数据库操作完成，响应时间长。异步处理可以先返回订单ID，后台线程慢慢处理数据库写入，提升用户体验。

**秒杀策略流程图设计**：

#### 同步版本流程（VoucherSeckillController)这里使用redisson

```
用户请求 → 校验秒杀活动 → 生成订单ID → 直接调用secondKill() → 扣库存+保存订单 → 返回结果
```

#### 异步单机版本流程（VoucherController - 内存队列）模拟redisLock使用

```
用户请求 → 校验秒杀活动 → Lua脚本校验 → 放入ArrayBlockingQueue → 返回订单ID
                                                              ↓
                                        后台线程 take() → RedisLock → paySuccess() → 扣库存+保存订单
```

#### 异步分布式版本流程（VoucherOrderController - Redis Stream）这里使用redisson

```
用户请求 → 校验秒杀活动 → Lua脚本校验（自动XADD到Stream）→ 返回订单ID
                                                         ↓
                                XREADGROUP读取 → 解析订单 → secondKill() → 扣库存+保存订单 → ACK确认
```

#### 数据库操作阶段

无论是同步还是异步，最终都需要执行以下数据库操作：

1. **一人一单校验**：在事务中基于用户ID和优惠券ID查询已存在订单
2. **原子库存扣减**：使用 MyBatis Plus 的乐观锁 CAS 操作保证库存扣减的原子性
3. **订单创建**：保存订单记录

> **注意**：同步版本直接在请求线程中执行数据库操作，而异步版本在后台线程池中执行。

```
用户请求 → Lua脚本校验（库存+重复下单）→ 成功
								  ↓ →放入异步队列 → 后台线程处理（扣库存+保存订单）
                                  ↓ →失败→直接返回
```

### 编码阶段

**部分代码实现**：

**创建优惠券**

```java
@PostMapping("/create")
public Result createVoucher(@RequestBody VoucherDTO voucherDTO) {
    Voucher voucher = BeanUtil.toBean(voucherDTO, Voucher.class);
    voucherService.save(voucher);
    // 创建秒杀活动并初始化Redis库存
    List<VoucherSeckill> voucherSeckillList = voucherDTO.getVoucherSeckillList().stream()
            .map(vs -> VoucherSeckill.builder()
                    .stock(vs.getStock())
                    .beginTime(vs.getBeginTime())
                    .endTime(vs.getEndTime())
                    .voucherId(voucher.getId())
                    .build())
            .toList();
    // 将库存同步到Redis
    for (VoucherSeckill vs : voucherSeckillList) {
        stringRedisTemplate.opsForValue().set(
            "voucherSeckill:stock:" + voucher.getId(), 
            vs.getStock().toString());
    }
    voucherSeckillService.saveBatch(voucherSeckillList);
    return Result.success("createVoucher");
}
```

**Lua脚本（redis-seckill.lua）**：

```lua
-- 参数：优惠券ID、用户ID
local voucherId = ARGV[1]
local userId = ARGV[2]

-- Redis Key定义
local stockKey = "voucherSeckill:stock:" .. voucherId
local orderKey = "voucherSeckill:order:" .. voucherId

-- 1. 判断库存
if tonumber(redis.call('get', stockKey)) <= 0 then
    return 1  -- 库存不足
end

-- 2. 判断是否已下单
if redis.call('sismember', orderKey, userId) > 0 then
    return 2  -- 重复下单
end

-- 3. 扣减库存
redis.call('incrby', stockKey, -1)

-- 4. 记录下单用户
redis.call('sadd', orderKey, userId)

-- 5. 成功
return 0
```

**异步秒杀控制器（VoucherOrderController）**：
```java
@PostMapping("/pay")
public Result redisproLock(@RequestBody VoucherOrder voucherOrder) {
    Long userId = ThreadLocalParam.getUserId();
    
    // 执行Lua脚本
    Long result = stringRedisTemplate.execute(REDIS_SCRIPT,
            List.of(),
            voucherOrder.getVoucherId().toString(), 
            userId.toString());
    
    if (result != 0) {
        return Result.error(result == 1 ? "库存不够" : "重复下单");
    }
    
    // 生成分布式ID
    long orderId = redisID.createId("order");
    
    // 设置订单信息
    voucherOrder.setId(orderId);
    voucherOrder.setUserId(userId);
    voucherOrder.setStatus(1L);
    
    // 放入异步队列
    boolean offer = orderQueue.offer(voucherOrder);
    if (!offer) {
        return Result.error("系统繁忙");
    }
    
    return Result.success(orderId);
}
```

**后台订单处理线程**：
```java
private class HandleOrderTask implements Runnable {
    @Override
    public void run() {
        while (true) {
            try {
                VoucherOrder voucherOrder = orderQueue.take();
                Long userId = voucherOrder.getUserId();
                Long voucherId = voucherOrder.getVoucherId();
                
                // 使用Redisson分布式锁防止重复处理
                RLock redisLock = redissonClient.getLock(
                    "redisson:voucherSeckill:" + userId + ":" + voucherId);
                
                try {
                    if (redisLock.tryLock(5, 10, TimeUnit.SECONDS)) {
                        voucherOrderService.paySuccess(voucherOrder);
                    }
                } finally {
                    if (redisLock.isHeldByCurrentThread()) {
                        redisLock.unlock();
                    }
                }
            } catch (Exception e) {
                log.error("订单处理异常: " + e.getMessage());
            }
        }
    }
}
```

###  问题修复阶段

**问题1**：库存超卖

**修复方案**：
- Redis中用Lua脚本原子扣减
- MySQL中用乐观锁 `gt(stock, 0).setSql("stock = stock - 1")`

**问题2**：重复下单

**修复方案**：
- Redis中用Set存储已下单用户ID（sismember判断）
- MySQL中查询已有订单记录

**问题3**：分布式锁误删

**修复方案**：使用Lua脚本释放锁，只有锁的持有者才能释放

### 三个秒杀控制器详解

本项目实现了三种不同架构的秒杀控制器，分别适用于不同的并发场景：

#### 1. VoucherSeckillController（同步版本）

**架构特点**：
- 直接操作数据库完成扣库存和保存订单
- 使用 Redisson 分布式锁保证一人一单
- 同步处理，用户请求需要等待数据库操作完成

**部分代码实现**：
```java
// VoucherSeckillController.java
@PostMapping("/pay")
public Result redisLock(@RequestBody VoucherOrder voucherOrder) {
    // 校验秒杀活动是否有效
    VoucherSeckill voucherSeckill = voucherSeckillService.voucherSeckillValid(voucherOrder.getVoucherId());
    if (voucherSeckill == null) {
        return Result.error("秒杀活动不存在或已结束");
    }
    
    // 获取当前用户ID，设置订单基础信息
    Long userId = ThreadLocalParam.getUserId();
    voucherOrder.setId(redisID.createId("orderId"));
    voucherOrder.setUserId(userId);
    voucherOrder.setStatus(1L);
    
    // 直接调用同步下单方法
    voucherOrderService.secondKill(voucherOrder);
    return Result.success("paySuccess");
}
```

**适用场景**：并发量较低的场景（单机几百QPS），实现简单，易于调试。

---

#### 2. VoucherController（异步版本 - 单机版本流程）

**架构特点**：
- 使用 Lua 脚本在 Redis 中完成库存校验和扣减
- 使用 `ArrayBlockingQueue` 内存队列存储待处理订单
- 单线程后台处理器从队列中取出订单完成数据库操作
- 使用自定义 `RedisLock` 分布式锁防止重复处理

**部分代码实现**：
```java
// VoucherController.java - 下单接口
@PostMapping("/pay")
public Result redisLock(@RequestBody VoucherOrder voucherOrder) {
    // 校验秒杀活动
    VoucherSeckill voucherSeckill = voucherSeckillService.voucherSeckillValid(voucherOrder.getVoucherId());
    if (voucherSeckill == null) {
        return Result.error("秒杀活动不存在或已结束");
    }
    
    Long userId = ThreadLocalParam.getUserId();
    Long orderId = redisID.createId("pay");
    
    // 执行Lua脚本：校验库存和重复下单
    Long result = stringRedisTemplate.execute(REDIS_SCRIPT,
            List.of(),
            voucherOrder.getVoucherId().toString(),
            userId.toString(),
            orderId.toString());
    
    if (result != 0) {
        return Result.error(result == 1 ? "库存不够" : "重复下单");
    }
    
    // 设置订单信息，放入内存队列异步处理
    voucherOrder.setId(orderId);
    voucherOrder.setUserId(userId);
    voucherOrder.setStatus(1L);
    boolean offer = orderQueue.offer(voucherOrder);
    if (!offer) {
        return Result.error("系统繁忙，请稍后重试");
    }
    return Result.success(orderId);
}

// 异步订单处理线程
private class HandleOrderTaskByList implements Runnable {
    @Override
    public void run() {
        while (true) {
            try {
                // 从队列中取出订单（阻塞等待）
                VoucherOrder voucherOrder = orderQueue.take();
                
                // 使用RedisLock分布式锁防止重复处理
                ILock redisLock = new RedisLock(stringRedisTemplate,
                        "redisson:voucherSeckill:" + voucherOrder.getUserId() + ":" + voucherOrder.getVoucherId());
                boolean locked = redisLock.getLocked(10);
                if (!locked) {
                    continue;
                }
                
                try {
                    // 执行实际的下单逻辑
                    voucherOrderService.paySuccess(voucherOrder);
                } finally {
                    redisLock.unlock();
                }
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                log.error("订单处理线程异常: " + e.getMessage());
                break;
            }
        }
    }
}
```

**适用场景**：中等并发场景（单机几千QPS），内存队列速度快，但重启后队列数据会丢失。

---

#### 3. VoucherOrderController（异步版本 - Redis Stream）

**架构特点**：
- 使用 Lua 脚本在 Redis 中完成库存校验和扣减
- 使用 **Redis Stream** 作为消息队列，支持消息持久化
- 使用消费组模式（Consumer Group），支持多实例部署
- 使用 Redisson 分布式锁防止重复处理

**部分代码实现**：
```java
// VoucherOrderController.java - 下单接口
@PostMapping("/pay")
public Result redisproLock(@RequestBody VoucherOrder voucherOrder) {
    // 校验秒杀活动
    VoucherSeckill voucherSeckill = voucherSeckillService.voucherSeckillValid(voucherOrder.getVoucherId());
    if (voucherSeckill == null) {
        return Result.error("秒杀活动不存在或已结束");
    }
    
    Long userId = ThreadLocalParam.getUserId();
    Long orderId = redisID.createId("order");
    
    // 执行Lua脚本：校验库存和重复下单
    Long result = stringRedisTemplate.execute(REDIS_SCRIPT,
            List.of(),
            voucherOrder.getVoucherId().toString(),
            userId.toString(),
            orderId.toString());
    
    if (result != 0) {
        return Result.error(result == 1 ? "库存不够" : "重复下单");
    }
    return Result.success(orderId);
}

// 异步订单处理线程（从Redis Stream读取）
private class HandleOrderTask implements Runnable {
    @Override
    public void run() {
        String streamKey = "stream.order";
        while (true) {
            // XREADGROUP GROUP g1 c1 COUNT 10 BLOCK 2000 STREAMS stream.order >
            List<MapRecord<String,Object,Object>> messageList = stringRedisTemplate.opsForStream().read(
                    Consumer.from("g1", "c1"),
                    StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
            
            if (messageList == null || messageList.isEmpty()) {
                continue;
            }
            
            // 解析订单信息
            MapRecord<String,Object,Object> record = messageList.get(0);
            Map<Object,Object> map = record.getValue();
            VoucherOrder voucherOrder = VoucherOrder.builder()
                    .voucherId(Long.parseLong(map.get("voucherId").toString()))
                    .userId(Long.parseLong(map.get("userId").toString()))
                    .id(Long.parseLong(map.get("orderId").toString()))
                    .build();
            
            // 执行下单逻辑
            voucherOrderService.secondKill(voucherOrder);
            
            // 确认消息已处理
            stringRedisTemplate.opsForStream().acknowledge(streamKey, "g1", record.getId());
        }
    }
}
```

**适用场景**：高并发场景（单机几万QPS），消息持久化，支持多实例部署，故障恢复能力强。

---

### 三种架构对比

| 维度 | VoucherSeckillController | VoucherController | VoucherOrderController |
| :--- | :--- | :--- | :--- |
| **处理方式** | 同步 | 异步（jvm队列） | 异步（Redis Stream） |
| **队列类型** | 无 | ArrayBlockingQueue | Redis Stream |
| **分布式锁** | Redisson | 自定义RedisLock | Redisson |
| **消息持久化** | 无 | 无 | 有 |
| **多实例支持** | 支持（锁保证） | 不支持（内存队列） | 支持（消费组） |
| **吞吐量** | 低（几百QPS） | 中（几千QPS） | 高（几万QPS） |
| **故障恢复** | 无状态 | 队列数据丢失 | 消息可恢复 |
| **适用场景** | 测试/低并发 | 中等并发 | 生产高并发 |

### 演进过程总结

**为什么从同步版演进到异步版？**
> A：同步版本在高并发下会导致数据库压力过大，响应时间变长。异步版本将热点操作转移到Redis，数据库操作异步化，大大提升了系统的吞吐量和响应速度。

**为什么从内存队列演进到Redis Stream？**
> A：内存队列在应用重启后数据会丢失，且不支持多实例部署。Redis Stream提供消息持久化和消费组机制，适合生产环境的高可用部署。

---

## 八、邮箱登录与验证码模块

### 需求阶段

**需求背景**：实现邮箱验证码登录功能，支持用户通过邮箱接收验证码进行身份验证登录。

**痛点**：
- 验证码发送需要异步处理，避免阻塞用户请求
- 验证码需要设置有效期，过期后失效
- 高并发场景下邮件发送需要削峰填谷

### 设计阶段

**设计思路**：

Q：为什么用Redis Stream异步发送邮件？
> A：邮件发送是IO密集型操作，直接在请求线程中发送会导致响应时间过长。使用Redis Stream作为消息队列，可以实现异步解耦，请求线程只负责生成验证码并存入队列，后台线程负责实际发送邮件。

Q：为什么验证码存在Redis而不是数据库？
> A：验证码是短期临时数据（10分钟过期），存入Redis可以利用其过期自动清理的特性，无需额外维护清理任务，且读写性能更高。

### 编码阶段

**策略流程图**：

```java
发送验证码 → LoginController/sendCode() → 生成6位验证码 → Redis存储（10分钟过期）→ XADD到Redis Stream → 返回结果
                                               ↓
                                       后台线程 XREADGROUP读取 → JavaMailSender发送邮件 → ACK确认
邮箱登录 → LoginController/loginByEmail() → Redis校验验证码 → 查询用户 → 生成JWT Token → 返回Token
```

**部分代码**：

```java
// LoginController.java - 发送验证码（异步）
String code = RandomUtil.randomString(6);  // 生成6位随机验证码
stringRedisTemplate.opsForValue().set("code:" + email, code, 10, TimeUnit.MINUTES);
// XADD到Redis Stream，异步发送邮件
stringRedisTemplate.opsForStream().add("valid:code:stream", Map.of("email", email, "code", code));
```

### 问题修复阶段

**问题1**：Stream消费组重复创建异常

**修复方案**：在 `@PostConstruct` 初始化方法中捕获异常，若消费组已存在则忽略错误

```java
@PostConstruct
public void init() {
    try {
        stringRedisTemplate.opsForStream().createGroup(STREAM_KEY, "g1");
        log.info("Redis Stream消费组创建成功");
    } catch (Exception e) {
        log.info("消费组已存在，跳过创建");
    }
    CODE_EXECUTOR.submit(new HandleCodeTask());
}
```

**问题2**：应用关闭时线程池未正确关闭

**修复方案**：在 `@PreDestroy` 方法中优雅关闭线程池

```java
@PreDestroy
public void destroy() {
    CODE_EXECUTOR.shutdown();
    try {
        if (!CODE_EXECUTOR.awaitTermination(10, TimeUnit.SECONDS)) {
            CODE_EXECUTOR.shutdownNow();
        }
    } catch (InterruptedException e) {
        CODE_EXECUTOR.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

---

## 九、关注管理模块

### 需求阶段

**需求背景**：实现用户之间的关注/取关功能，支持查询关注状态和共同关注的用户。

**痛点**：
- 关注关系需要实时查询
- 共同关注用户查询需要高效的集合交集运算
- 关注操作需要同时更新数据库和缓存

### 设计阶段

**设计思路**：

Q：为什么用Redis Set存储关注关系？
> A：Set支持高效的集合操作（add、remove、contains、intersect），非常适合实现关注关系的管理。共同关注功能可以通过Set的intersect操作快速获取两个用户关注集合的交集。

Q：为什么同时更新数据库和Redis？
> A：数据库用于持久化存储，保证数据不丢失；Redis用于高性能查询，提高关注状态查询和共同关注计算的响应速度。采用双写策略，先写数据库再写Redis。

### 编码阶段

**策略流程图**：

```java
关注请求 → UseFollowController/useFollow() → MySQL保存关注关系 → Redis Set添加（follow:{userId}）→ 返回结果
取关请求 → UseFollowController/useFollow() → MySQL删除关注关系 → Redis Set移除（follow:{userId}）→ 返回结果
查询关注状态 → UseFollowController/getUserFollow() → Redis Set/IsMember判断 → 返回结果
查询共同关注 → UseFollowController/getUserFollowCommon() → Redis Set/ZIntersect → 查询用户信息 → 返回结果
```

**部分代码**：

```java
// UseFollowController.java - 共同关注查询（Redis Set交集）
Set<String> commonSet = stringRedisTemplate.opsForSet().intersect(
    "follow:" + userId, "follow:" + targetUserId);
List<Long> ids = commonSet.stream().map(Long::parseLong).toList();
List<User> userList = userService.listByIds(ids);
```

### 问题修复阶段

**问题1**：关注状态返回不够直观 ✅ 已修复

**修复方案**：返回更明确的状态描述（"已关注"/"未关注"），已在 `UseFollowController.java` 中应用

```java
// 将用户ID转换为用户信息列表
 List<Long> ids = commonSet.stream().map(s -> Long.parseLong(s)).toList();
//降低负载，ids = 一次
 List<User> userList = userService.listByIds(ids);
```

---

## 十、签到管理模块

### 需求阶段

**需求背景**：实现用户签到功能，支持每日签到、补签和签到统计，激励用户活跃。

**痛点**：
- 签到记录数据量大，按月存储需要高效的存储空间
- 签到状态需要快速查询和统计
- 补签功能需要支持指定日期签到

### 设计阶段

**设计思路**：

Q：为什么用Redis BitMap存储签到记录？
> A：BitMap（位图）是一种高效的位存储结构，每个用户每天的签到状态只需要1个位（0或1）。一个月最多31天，只需要31个位（约4字节）就能存储一个用户一个月的签到记录，极大节省存储空间。

Q：为什么用bitField命令统计签到次数？

> A：bitField可以批量获取位图中的位数据，将指定位数的二进制数据转换为十进制数，然后通过统计二进制中1的个数来快速计算签到天数。
>
> ```
> 第一种long signedDays = Long.bitCount(num10);
> 第二种for (int i = 0; i < now.getDayOfMonth(); i++){
>             if ((num10 & 1) == 1){
>                 signedDays++;
>             }
>             num10 = num10 >>>1;
>         }
> ```

### 编码阶段

**Redis Key设计**：

```
sign:{userId}:{yyyy-MM}  // 用户签到位图Key，例如 sign:1:2024-01
```

**策略流程图**：

```java
签到请求 → SignController/createSign() → Redis SetBit设置签到位（offset=日期天数）→ 返回结果
补签请求 → SignController/backSign() → Redis SetBit设置指定日期签到位 → 返回结果
统计请求 → SignController/CountSign() → Redis BitField获取位图 → 统计1的个数 → 返回签到/缺勤数
保存请求（按月保存） → SignController/CountSign() → Redis BitField获取位图 → MySQL保存统计数据
```

**部分代码**：

```java
// SignController.java - 每日签到（Redis BitMap）
String key = "sign:" + userId + ":" + month;
int dayOfMonth = LocalDate.now().getDayOfMonth();
stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
```

### 问题修复阶段

**问题1**：签到统计时，如果当月没有任何签到记录，result为空导致空指针异常 ✅ 已修复

**修复方案**：使用 `CollectionUtil.isEmpty()` 判断结果是否为空，为空时返回0

```java
if (CollectionUtil.isEmpty(result)) {
    return Result.success(0);
}
```

**问题2**：补签接口未校验日期是否合法（如日期格式错误、日期超出当月范围）

**修复方案**：在补签接口中添加日期格式校验和范围校验，防止非法日期操作

---

## 十一、店铺管理模块

### 需求阶段

**需求背景**：实现店铺管理功能，支持创建店铺和基于分类/地理位置查询附近店铺，为用户提供探店搜索服务。

**痛点**：
- 店铺查询需要支持按距离排序，传统数据库查询效率低
- 店铺数据量较大，分页查询需要高效的游标策略
- 店铺分类与位置信息需要关联存储，便于快速检索

### 设计阶段

**设计思路**：

Q：为什么用Redis GEO存储店铺位置？
> A：Redis GEO是专门为地理位置数据设计的数据结构，支持高效的距离计算和范围查询。使用GEO可以快速找到指定坐标附近的店铺，并且按距离排序，这是传统数据库难以实现的。

Q：为什么设计两种查询模式？
> A：当用户未提供位置信息时，使用基于ID的游标分页（滚动分页），简单高效；当用户提供经纬度时，使用Redis GEO按距离排序查询附近店铺，满足LBS（位置服务）需求。

### 编码阶段

**策略流程图**：

```java
创建店铺 → ShopController/createShop() → MySQL保存Shop和ShopType → Redis GEO存储位置（shopType:{typeId}）→ 返回结果
查询店铺列表 → ShopController/ofType()
    ├─ 无经纬度 → MySQL游标分页查询（按ID升序，每页5条）→ 返回结果
    └─ 有经纬度 → Redis GEO搜索（5公里范围，按距离排序）→ 获取店铺ID列表 → MySQL查询详情 → 返回结果
```

**部分代码**：

```java
// ShopController.java - Redis GEO搜索附近店铺
GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
    SHOP_TYPE + typeId,
    GeoReference.fromCoordinate(x, y),
    new Distance(5, Metrics.KILOMETERS),
    RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(5).sortAscending()
);
```

### 问题修复阶段

**问题1**：Redis GEO搜索结果为空时可能导致空指针异常 ✅ 已修复

**修复方案**：使用 `CollectionUtil.isEmpty()` 判断结果是否为空，为空时直接返回空列表

```java
if (CollectionUtil.isEmpty(results)){
    return Result.success(null);
}
```

---

# 核心组件设计

### 1. Redis分布式ID生成器（RedisID）

**设计思路**：

Q：为什么不用UUID？
> A：UUID是随机字符串，无序，作为数据库主键会导致索引分裂，影响性能。而且UUID太长（36位），存储和传输成本高。

Q：为什么不用数据库自增ID？
> A：数据库自增ID在分布式环境下需要额外处理（比如分库分表），而且生成ID需要访问数据库，性能不如Redis。

Q：ID结构为什么是 1位符号位+时间戳(31位) + 序号(32位)？
> A：0作为符号位，正数自增，31位时间戳可以表示约68年（2^31秒 ≈ 68年），从2020年开始够用。32位序号可以表示约42亿，足够单日并发使用。

**代码实现**：

```java
@Component
public class RedisID {
    // 基准时间：2020-01-01 00:00:00 UTC
    private final static long BEGIN_TIME = 1577836800L;
    // 32位序号最大值
    private static final long MAX_SEQ = 0xFFFFFFFFL;

    public long createId(String prefix) {
        long nowSeconds = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond();
        long timestamp = nowSeconds - BEGIN_TIME;
        
        String date = ZonedDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        String key = "icr:" + prefix + ":" + date;
        long count = stringRedisTemplate.opsForValue().increment(key);
        
        return timestamp << 32 | count;
    }
}
```

### 2. Redis分布式锁（RedisLock）

**设计思路**：

Q：为什么锁的value要包含UUID+线程ID？
> A：防止锁误删问题。如果不校验value，线程A的锁过期了，线程B获取了锁，此时线程A执行完业务去释放锁，就会把线程B的锁删掉。

Q：为什么用Lua脚本释放锁？
> A：判断锁是否属于自己和删除锁需要原子性执行，否则在判断和删除之间锁可能过期被其他线程获取。

Q：分布式锁持续时间？

> A：许多主流的分布式锁框架（如 Java 生态中著名的 **Redisson**）在未指定超时时间时，其内置的 Watchdog（看门狗）默认续期时间就是 **30秒**。

**代码实现**：

```java
public class RedisLock implements ILock {
    private static final String KEY_PREFIX = "lock:";
    private static final String VALUE_PREFIX = UUID.randomUUID().toString() + ":";
    
    @Override
    public boolean getLocked(long timeoutSec) {
        String key = KEY_PREFIX + name;
        String value = VALUE_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue()
            .setIfAbsent(key, value, timeoutSec, TimeUnit.SECONDS);
        return Optional.ofNullable(success).orElse(false);
    }
    
    @Override
    public void unlock() {
        String key = KEY_PREFIX + name;
        String value = VALUE_PREFIX + Thread.currentThread().getId();
        // Lua脚本：只有锁的持有者才能释放
        stringRedisTemplate.execute(REDISSCRIPT, List.of(key), value);
    }
}
```

**Lua脚本（redis-unlock.lua）**：
```lua
local id = redis.call('get', KEYS[1])
if id == ARGV[1] then
    return redis.call('del', KEYS[1])
end
return 0
```

# 依赖说明

### 用户管理功能依赖
| 依赖 | 版本 | 功能支撑 |
| :--- | :--- | :--- |
| Spring Boot | 3.3.8 | 应用框架，自动配置数据源、Redis等基础设施 |
| Spring Boot Starter Web | 3.3.8 | UserController提供REST接口（注册、登录、信息修改、头像上传、密码修改）；注册LoginInterceptor和ReLoginInterceptor拦截器校验登录状态 |
| Spring Boot Starter Security | 3.3.8 | BCryptPasswordEncoder实现密码单向哈希加密（内置随机盐，每次encode结果不同）；SecurityConfig注册PasswordEncoder Bean供全局注入使用；matches()方法验证密码，而非解密 |
| MyBatis Plus | 3.5.9 | UserMapper继承BaseMapper实现用户数据CRUD；AutoMetaObjectHandler自动填充create_time、update_time等元数据字段 |
| JJWT API/Impl/Jackson | 0.12.6 | JwtUtil生成登录Token，ReLoginInterceptor验证Token并实现滑动过期策略（每次请求刷新Redis中Token有效期） |
| Spring Boot Starter Data Redis | 3.3.8 | 存储用户Token（`bigevent:{userId}`）和邮箱验证码（`code:{email}`，10分钟过期） |
| Spring Boot Starter Validation | 3.3.8 | @NotNull、@Size等注解校验注册和登录参数的合法性 |
| Aliyun SDK OSS | 3.17.4 | AliOssUtil实现用户头像上传到阿里云OSS，返回CDN访问URL |

### 事件文章管理功能依赖
| 依赖 | 版本 | 功能支撑 |
| :--- | :--- | :--- |
| MyBatis Plus | 3.5.9 | ArticleMapper实现文章数据CRUD；AutoMetaObjectHandler自动填充创建人ID和时间 |
| Spring Boot Starter Data Redis | 3.3.8 | **缓存策略**：ArticleServiceImpl使用分布式锁（`setIfAbsent`）5秒+逻辑过期（RedisData包装类），防止缓存击穿30秒；文章数据缓存**30分钟 - 2小时**（逻辑过期）本文选择**30分钟**负载平衡；更新/删除后主动删除缓存保证一致性 |
| Spring Boot Starter Validation | 3.3.8 | 自定义@ArticleStatus注解校验文章状态只能为"已发布"或"草稿" |
| Aliyun SDK OSS | 3.17.4 | 文章封面图片上传到阿里云OSS |
| Hutool All | 5.8.36 | BeanUtil进行缓存数据对象转换；StrUtil判空；JSONUtil序列化/反序列化Redis数据 |

### 分类管理功能依赖
| 依赖 | 版本 | 功能支撑 |
| :--- | :--- | :--- |
| MyBatis Plus | 3.5.9 | CategoryMapper实现分类数据CRUD；AutoMetaObjectHandler自动填充创建人ID和时间 |
| Spring Boot Starter Data Redis | 3.3.8 | **缓存策略**：CategoryServiceImpl使用分布式锁5秒+逻辑过期，防止缓存击穿30秒；分类数据缓存**10分钟 - 30分钟**（逻辑过期），本文选择**30分钟****负载平衡；更新/删除后主动删除缓存 |
| Spring Boot Starter Validation | 3.3.8 | @NotNull、@Size等注解校验分类名称和别名参数 |
| Hutool All | 5.8.36 | BeanUtil进行对象属性拷贝；StrUtil判空；JSONUtil序列化/反序列化 |

### 优惠券管理功能依赖
| 依赖 | 版本 | 功能支撑 |
| :--- | :--- | :--- |
| MyBatis Plus | 3.5.9 | VoucherMapper、VoucherSeckillMapper、VoucherOrderMapper实现优惠券数据CRUD；AutoMetaObjectHandler自动填充create_time、update_time等元数据字段 |
| Spring Boot Starter Data Redis | 3.3.8 | **分布式ID生成**：RedisID类基于Redis自增计数器实现全局唯一订单ID（0作为符号位+时间戳31位+序号32位）；存储秒杀库存信息；**分布式锁**：RedisLock基于setIfAbsent实现锁获取，Lua脚本原子释放 |
| Spring Boot Starter Validation | 3.3.8 | 参数校验支持 |
| Spring Boot Starter Web | 3.3.8 | 提供@Transactional注解实现秒杀订单事务一致性（通过spring-tx传递依赖） |
| Hutool All | 5.8.36 | BeanUtil进行对象属性拷贝（VoucherDTO转Voucher）；UUID生成分布式锁唯一标识 |

### 探店博文功能依赖
| 依赖 | 版本 | 功能支撑 |
| :--- | :--- | :--- |
| MyBatis Plus | 3.5.9 | BlogMapper实现探店博文数据CRUD；AutoMetaObjectHandler自动填充create_time、update_time等元数据字段 |
| Spring Boot Starter Data Redis | 3.3.8 | **点赞功能**：使用Redis ZSet存储点赞用户ID和时间戳，支持原子操作；实时查询点赞状态和热门点赞用户 |
| Hutool All | 5.8.36 | BeanUtil进行对象属性拷贝（BlogDTO转Blog）；BooleanUtil判断布尔值 |

### 评论与回复功能依赖
| 依赖 | 版本 | 功能支撑 |
| :--- | :--- | :--- |
| MyBatis Plus | 3.5.9 | BlogCommentsMapper实现评论数据CRUD；支持多级回复查询（parent_id、answer_id） |
| Spring Boot Starter Data Redis | 3.3.8 | 评论点赞功能支持；评论列表缓存 |

### 文件管理功能依赖
| 依赖 | 版本 | 功能支撑 |
| :--- | :--- | :--- |
| Spring Boot Starter Web | 3.3.8 | MultipartFile文件上传支持；文件下载响应流处理 |
| Aliyun SDK OSS | 3.17.4 | AliOssUtil实现文件上传到阿里云OSS，支持CDN加速访问 |

### 邮箱登录与验证码功能依赖
| 依赖 | 版本 | 功能支撑 |
| :--- | :--- | :--- |
| Spring Boot Starter Mail | 3.3.8 | JavaMailSender实现邮件发送功能，支持SMTP协议 |
| Spring Boot Starter Data Redis | 3.3.8 | **Redis Stream**：异步发送验证码消息队列（`valid:code:stream`），消费组模式支持多实例部署；存储验证码（`code:{email}`，10分钟过期）和登录Token（`bigevent:{userId}`） |
| JJWT API/Impl/Jackson | 0.12.6 | JwtUtil生成邮箱登录Token，支持自定义载荷和过期时间 |
| Spring Boot Starter Validation | 3.3.8 | @Email注解校验邮箱格式合法性 |

### 关注管理功能依赖
| 依赖 | 版本 | 功能支撑 |
| :--- | :--- | :--- |
| MyBatis Plus | 3.5.9 | UserFollowMapper实现关注关系数据CRUD；UserMapper查询用户信息 |
| Spring Boot Starter Data Redis | 3.3.8 | **Redis Set**：存储用户关注列表（`follow:{userId}`），支持add/remove/intersect操作；共同关注通过Set交集运算高效计算 |
| Hutool All | 5.8.36 | BooleanUtil判断关注状态布尔值 |

### 签到管理功能依赖
| 依赖 | 版本 | 功能支撑 |
| :--- | :--- | :--- |
| Spring Boot Starter Data Redis | 3.3.8 | **Redis BitMap**：存储用户签到位图（`sign:{userId}:{yyyy-MM}`），使用SetBit设置签到位，BitField批量获取位数据；每个用户每月仅需约4字节存储 |
| Hutool All | 5.8.36 | CollectionUtil判断签到统计结果是否为空；BeanUtil对象属性拷贝 |

### 店铺管理功能依赖
| 依赖 | 版本 | 功能支撑 |
| :--- | :--- | :--- |
| MyBatis Plus | 3.5.9 | ShopMapper、ShopTypeMapper实现店铺和分类数据CRUD；AutoMetaObjectHandler自动填充create_time、update_time等元数据字段 |
| Spring Boot Starter Data Redis | 3.3.8 | **Redis GEO**：存储店铺位置信息（`shopType:{typeId}`），支持按坐标搜索附近店铺，5公里范围内按距离排序；GeoSearch命令实现高效的LBS查询 |
| Hutool All | 5.8.36 | BeanUtil进行对象属性拷贝（ShopDTO转Shop/ShopType）；CollectionUtil判断GEO搜索结果是否为空 |

---

### 秒杀功能依赖

| 依赖 | 版本 | 功能支撑 |
| :--- | :--- | :--- |
| MyBatis Plus | 3.5.9 | VoucherMapper、VoucherSeckillMapper、VoucherOrderMapper实现优惠券数据CRUD；@Transactional注解实现事务一致性 |
| Spring Boot Starter Data Redis | 3.3.8 | **Lua脚本**：原子性校验库存和重复下单；**Redis Stream**：异步订单消息队列（消费组模式支持多实例部署）；**分布式ID生成**：RedisID类生成全局唯一订单ID |
| Spring Boot Starter Web | 3.3.8 | VoucherSeckillController、VoucherController、VoucherOrderController提供三种秒杀接口 |
| Spring Boot Starter Validation | 3.3.8 | 参数校验支持 |
| Hutool All | 5.8.36 | BeanUtil对象属性拷贝（VoucherDTO转Voucher）；UUID生成分布式锁唯一标识 |
| Redisson | 3.37.0 | 分布式锁实现一人一单限制；Watchdog自动续期防止锁过期 |

---

#### 对比分析

**问题1：直接在Controller层用synchronized**

```java
// 其他人写法 - 错误！
@RequestMapping("/seckill")
public synchronized Result seckill(Long voucherId) {
    // 扣库存逻辑...
}
```
> 本项目改进：synchronized只在单实例有效，集群环境下必须用Redis分布式锁。

**问题2：不校验锁的持有者就释放**

```java
// 其他人写法 - 错误！
public void unlock() {
    stringRedisTemplate.delete(key);  // 可能删除其他线程的锁
}
```
> 本项目改进：用Lua脚本校验锁的value，只有持有者才能释放。

**问题3：同步处理订单**
```java
// 其他人写法 - 性能差！
@RequestMapping("/seckill")
public Result seckill(Long voucherId) {
    // 同步扣库存...
    // 同步保存订单...
    return Result.success();
}
```
> 本项目改进：先用Lua脚本在Redis中完成校验，再放入异步队列，后台线程处理数据库写入。

---

# 前端功能演示

| 登录页面       | ![登录页面](说明/原型功能/1.png)    |
| -------------- | ----------------------------------- |
| 分类管理       | ![登录页面](说明/原型功能/2.png)    |
| 文章列表       | ![登录页面](说明/原型功能/3.png)    |
| hot查看        | ![登录页面](说明/原型功能/4.png)    |
| 用户设置       | ![登录页面](说明/原型功能/5.png)    |
| 用户信息       | ![登录页面](说明/原型功能/6.png)    |
| 用户密码       | ![登录页面](说明/原型功能/7.png)    |
| 邮箱发送验证码 | ![登录页面](说明/原型功能/邮箱.png) |

------

