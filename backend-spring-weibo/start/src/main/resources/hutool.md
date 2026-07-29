1. 引入依赖
   首先确保你的项目中引入了 Hutool（通常引入 hutool-all 即可）：
2.字符串操作 (StrUtil)
   比 JDK 自带的 StringUtils 更强大，处理 null 更安全。

判空：

    boolean isEmpty = StrUtil.isEmpty("");        // true
    boolean isBlank = StrUtil.isBlank("   ");     // true (建议用这个，会过滤空格)
    boolean hasText = StrUtil.isNotBlank("hutool"); // true
裁剪：

    String str = StrUtil.trim(" abc ");           // "abc"
    String removed = StrUtil.removeSuffix("image.jpg", ".jpg"); // "image"
格式化：

    String template = "你好，我是{}，今天是{}。";
    String result = StrUtil.format(template, "张三", "周五"); 
    // "你好，我是张三，今天是周五。"
其他：sub (截取)、split (分割)、equals (比较，防空指针)。
3. 日期时间处理 (DateUtil)
   Hutool 的日期处理极大地简化了 JDK 原生 Date 和 Calendar 的复杂操作。

解析与格式化：

    // 字符串转日期（自动识别常用格式）
    Date date = DateUtil.parse("2023-10-01");
    
    // 日期转字符串
    String format = DateUtil.format(date, "yyyy-MM-dd HH:mm:ss");
日期偏移（加减）：

    Date tomorrow = DateUtil.offsetDay(new Date(), 1); // 明天
    Date nextMonth = DateUtil.offsetMonth(new Date(), 1); // 下个月
获取日期部分：

    int year = DateUtil.year(new Date());
    int month = DateUtil.month(new Date()) + 1; // 0-11，需+1
常用时间：

    Date now = DateUtil.date(); // 当前时间，相当于 new Date()
    String today = DateUtil.today(); // "2023-10-01"
星座/生肖：

    String chineseZodiac = DateUtil.getChineseZodiac(1990); // "马"
    String zodiac = DateUtil.getZodiac(Month.JANUARY.getValue(), 20); // "水瓶座"
4. 集合与数组操作 (CollUtil, ArrayUtil)
   处理 List、Set、Map 和数组的工具。

判空：

    CollUtil.isEmpty(list);
    CollUtil.isNotEmpty(map);
集合操作：

    // 将数组转列表
    List<String> list = CollUtil.newArrayList("a", "b", "c");
    
    // 分页
    List<Integer> subList = CollUtil.page(list, 0, 2); // 取前2个
    
    // 连接字符串
    String joinStr = CollUtil.join(list, ","); // "a,b,c"
数组操作：

    // 数组转包装类数组
    Integer[] ints = ArrayUtil.wrap(new int[]{1, 2, 3});
    
    // 判断包含
    boolean contains = ArrayUtil.contains(ints, 2);
5. 对象/Bean 操作 (BeanUtil, ObjectUtil)
   解决对象拷贝、反射、Map 和 Bean 互转等痛点。

Bean 属性拷贝（类似 Spring 的 BeanUtils，但不用引入 Spring）：

    User source = new User();
    UserDTO target = new UserDTO();
    BeanUtil.copyProperties(source, target); 
Map 转 Bean：

    Map<String, Object> map = new HashMap<>();
    map.put("name", "Jack");
    User user = BeanUtil.mapToBean(map, User.class, false);
对象转 Map：

    Map<String, Object> map = BeanUtil.beanToMap(user);
通用判空：

    ObjectUtil.isNull(obj);
    ObjectUtil.isNotEmpty(obj);
    String defaultStr = ObjectUtil.defaultIfNull(str, "默认值");
6. 加密解密 (SecureUtil)
   提供了常见的加密算法封装，非常简单。

MD5：

    String md5 = SecureUtil.md5("123456");
SHA256：

    String sha256 = SecureUtil.sha256("123456");
AES 加密/解密：

    String content = "test中文";
    // 随机生成密钥
    AES aes = SecureUtil.aes();
    byte[] encrypt = aes.encrypt(content);
    byte[] decrypt = aes.decrypt(encrypt);
7. HTTP 请求 (HttpUtil)
   发起 HTTP 请求，不需要依赖 Apache HttpClient 或 OkHttp。

GET 请求：

    String response = HttpUtil.get("https://www.baidu.com") ;
POST 请求：

    HashMap<String, Object> map = new HashMap<>();
    map.put("name", "test");
    String result = HttpUtil.post("https://api.example.com" , map);
下载文件：

    // 下载图片到本地
    HttpUtil.downloadFile("http://example.com/image.jpg" , FileUtil.file("d:/a.jpg"));
8. IO 流操作 (IoUtil, FileUtil)
   处理文件读写、流复制。

读取文件内容：

    // 读取文件为字符串
    String content = FileUtil.readUtf8String("d:/test.txt");
    
    // 按行读取
    List<String> lines = FileUtil.readLines("d:/test.txt", "UTF-8");
写入文件：

    String str = "Hello Hutool";
    // 自动创建父目录，覆盖写入
    FileUtil.writeUtf8String(str, "d:/test.txt");
流操作：

    // 从输入流读取（自动关闭流）
    String str = IoUtil.read(inputStream, "UTF-8");
9. JSON 处理 (JSONUtil)
   轻量级的 JSON 处理工具，也可切换为 Jackson/Gson 实现。

转 JSON 字符串：

    User user = new User();
    String json = JSONUtil.toJsonStr(user);
解析 JSON 字符串：

    String jsonStr = "{\"name\":\"Jack\",\"age\":20}";
    JSONObject jsonObject = JSONUtil.parseObj(jsonStr);
    String name = jsonObject.getStr("name");
转 JSON 对象/数组：

    JSONArray array = JSONUtil.parseArray("[1,2,3]");
10. 身份证、号码、ID生成 (IdUtil)
    生成唯一 ID 的工具。

UUID：

    String uuid = IdUtil.randomUUID(); // 带有 "-" 的
    String simpleUUID = IdUtil.simpleUUID(); // 无 "-"
雪花算法：

    long id = IdUtil.getSnowflakeNextId(); // 生成分布式唯一ID
11. 其他常用工具
    NumberUtil：数值格式化、保留小数、随机数。
    double d = 12345.12345;
    String format = NumberUtil.decimalFormat("#.##", d); // "12345.12"
    BooleanUtil：布尔值判断转换。
    ClassUtil：类操作，如获取包名、方法名。
    Validator：校验，如 isEmail、isMobile。