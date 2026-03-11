# 🛍️ RAG 智能商品推荐系统

基于 LangChain4j、Spring Boot、Elasticsearch 和 OpenAI 构建的生产级 RAG (Retrieval-Augmented Generation) 商品推荐系统。

## ✨ 特性

- 🤖 **自然语言搜索** - 用户可以用自然语言描述需求，如"适合夏天跑步的轻便运动鞋，预算500以内"
- 🔍 **混合检索** - 结合向量相似度搜索 (70%) 和 BM25 关键词搜索 (30%)
- 📝 **智能推荐理由** - LLM 自动生成商品推荐理由
- 🎯 **高级过滤** - 支持价格区间、分类、品牌、库存等过滤条件
- 🌐 **Web 界面** - 现代化的响应式 UI，支持移动端

## 🏗️ 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.3.6 |
| 语言 | Java | 17 |
| AI 框架 | LangChain4j | 0.36.2 |
| 搜索引擎 | Elasticsearch | 8.17.0 |
| LLM | 通义千问 (OpenAI 兼容) | qwen-plus |
| Embedding | text-embedding-v4 | 1024 维 |
| 构建工具 | Maven | 3.x |

## 📋 系统要求

- **Java**: JDK 17+ (必须使用 Java 17，Lombok 与 Java 22+ 不兼容)
- **Maven**: 3.6+
- **Elasticsearch**: 8.x (支持向量搜索)
- **内存**: 至少 2GB 可用内存

## 🚀 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd rag_agent_demo
```

### 2. 启动 Elasticsearch

使用 Docker 启动 Elasticsearch：

```bash
docker run -d \
  --name elasticsearch \
  -p 9200:9200 \
  -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  elastic/elasticsearch:8.17.0
```

或使用阿里云 Elasticsearch 服务。

### 3. 配置应用

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200  # 或你的 ES 地址
    username: elastic
    password: your-password

openai:
  api:
    key: your-api-key           # 通义千问 API Key
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
```

### 4. 编译运行

```bash
# 确保 JAVA_HOME 指向 Java 17
export JAVA_HOME=/path/to/java17

# 编译
mvn clean compile

# 运行
mvn spring-boot:run
```

### 5. 访问应用

| 页面 | URL |
|------|-----|
| **首页** | http://localhost:8080 |
| **API 文档** | http://localhost:8080/swagger-ui.html |
| **健康检查** | http://localhost:8080/actuator/health |

## 📖 API 文档

### 商品推荐 API

**请求**

```http
POST /api/v1/recommendations
Content-Type: application/json

{
  "query": "适合夏天跑步的轻便运动鞋，预算500以内",
  "topK": 10,
  "filters": {
    "minPrice": 100,
    "maxPrice": 500,
    "categories": ["鞋类"],
    "brands": ["Nike", "李宁"],
    "inStock": true
  }
}
```

**响应**

```json
{
  "query": "适合夏天跑步的轻便运动鞋，预算500以内",
  "recommendations": [
    {
      "id": "1001",
      "name": "Nike Pegasus 40",
      "description": "轻便透气跑步鞋...",
      "category": "鞋类",
      "price": 499.0,
      "brand": "Nike",
      "reason": "轻便透气，适合夏季跑步",
      "imageUrl": "https://..."
    }
  ],
  "totalResults": 5,
  "processingTimeMs": 1234,
  "modelUsed": "qwen-plus",
  "timestamp": "2026-03-11T10:00:00"
}
```

### 其他 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/recommendations` | POST | 获取商品推荐 |
| `/api/v1/products/index` | POST | 索引单个商品 |
| `/api/v1/products/batch-index` | POST | 批量索引商品 |
| `/api/v1/products/{id}` | DELETE | 删除商品 |
| `/actuator/health` | GET | 健康检查 |

## 📁 项目结构

```
rag_agent_demo/
├── src/main/
│   ├── java/com/example/rag/
│   │   ├── RagApplication.java          # 启动类
│   │   ├── config/                      # 配置类
│   │   │   ├── ElasticsearchConfig.java
│   │   │   ├── LangChain4jConfig.java
│   │   │   └── OpenAIProperties.java
│   │   ├── controller/                  # 控制器
│   │   │   ├── RecommendationController.java
│   │   │   ├── ProductController.java
│   │   │   └── HealthController.java
│   │   ├── dto/                         # 数据传输对象
│   │   │   ├── RecommendationRequest.java
│   │   │   └── RecommendationResponse.java
│   │   ├── model/                       # 数据模型
│   │   │   ├── Product.java
│   │   │   ├── ProductDocument.java
│   │   │   └── SearchFilters.java
│   │   └── service/                     # 业务逻辑
│   │       ├── ProductIndexService.java
│   │       ├── VectorSearchService.java
│   │       └── RecommendationService.java
│   └── resources/
│       ├── application.yml              # 应用配置
│       ├── static/                      # 静态资源
│       │   ├── index.html               # 首页
│       │   ├── js/app.js                # 前端逻辑
│       │   └── images/img.png           # 默认图片
│       ├── data/products.json           # 商品数据
│       └── mapping/elasticsearch-mapping.json
├── pom.xml                              # Maven 配置
└── README.md                            # 项目文档
```

## 🔧 配置说明

### RAG 配置

```yaml
rag:
  recommendation:
    default-top-k: 10        # 默认返回数量
    max-top-k: 50            # 最大返回数量
    search:
      hybrid-weight-vector: 0.7    # 向量搜索权重
      hybrid-weight-bm25: 0.3     # BM25 搜索权重
```

### OpenAI 配置

```yaml
openai:
  api:
    key: your-api-key
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    timeout: 120s
    embedding:
      model: text-embedding-v4
      dimensions: 1024
    llm:
      model: qwen-plus
      max-tokens: 2000
      temperature: 0.7
```

## 🎯 核心原理

### RAG 流程

```
用户查询
    ↓
Query Embedding (向量化)
    ↓
Elasticsearch 混合检索
    ├─ 向量相似度搜索 (70%)
    └─ BM25 关键词搜索 (30%)
    ↓
召回候选商品
    ↓
LLM 生成推荐列表和理由
    ↓
返回结构化 JSON 响应
```

### 混合检索

结合了两种检索方式的优势：

- **向量搜索**: 捕捉语义相似性，理解用户意图
- **BM25 搜索**: 精确匹配关键词，确保结果相关性

通过 Elasticsearch 的 `boost` 参数实现权重配比。

## 🧪 测试

```bash
# 运行所有测试
mvn test

# 运行特定测试
mvn test -Dtest=RecommendationServiceTest
```

## 🐛 故障排查

### Elasticsearch 连接失败

```
java.net.ConnectException: Connection refused
```

**解决方案**:
1. 检查 Elasticsearch 是否运行: `curl http://localhost:9200`
2. 确认 `application.yml` 中的 ES 配置正确
3. 检查防火墙设置

### API Key 错误

```
401 Unauthorized
```

**解决方案**:
1. 检查 API Key 是否正确
2. 确认 API Key 有足够的配额
3. 检查 base-url 配置

### 编译错误

```
package lombok does not exist
```

**解决方案**:
1. 确保使用 Java 17: `java -version`
2. 在 IDE 中启用注解处理 (Annotation Processing)
3. 重新导入 Maven 依赖

## 📝 开发指南

### 添加新的过滤条件

1. 在 `SearchFilters.java` 添加字段
2. 在 `VectorSearchService.buildFilterQueries()` 实现过滤逻辑
3. 更新前端 `index.html` 添加输入控件

### 修改搜索权重

编辑 `application.yml`:

```yaml
rag:
  search:
    hybrid-weight-vector: 0.7  # 调整向量搜索权重
    hybrid-weight-bm25: 0.3    # 调整 BM25 搜索权重
```

### 切换 LLM 模型

编辑 `application.yml`:

```yaml
openai:
  llm:
    model: gpt-4o-mini  # 或其他支持的模型
```

## 🚢 部署

### Docker 部署

```bash
# 构建镜像
docker build -t rag-recommendation:latest .

# 运行容器
docker run -d \
  -p 8080:8080 \
  -e ES_URIS=http://elasticsearch:9200 \
  -e OPENAI_API_KEY=your-key \
  rag-recommendation:latest
```

### 生产环境建议

- 使用外部 Elasticsearch 集群
- 配置 API 网关进行负载均衡
- 启用 Spring Actuator 监控
- 配置日志收集 (ELK)
- 设置资源限制 (JVM 参数)

## 📄 许可证

MIT License

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📧 联系方式

如有问题，请提交 Issue 或联系项目维护者。

---

**Powered by LangChain4j + Elasticsearch + OpenAI**
