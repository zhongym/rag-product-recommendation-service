# 🛍️ RAG 商品推荐服务

基于 `Spring Boot + LangChain4j + Elasticsearch + OpenAI 兼容接口` 的商品推荐系统。  
系统流程为：自然语言查询 -> 混合检索（向量 + BM25）-> LLM 生成推荐理由 -> 返回结构化结果。

> 让“用户一句话需求”直接变成“可解释的商品推荐结果”。

## 🌟 产品亮点

| 亮点 | 价值 |
| --- | --- |
| ⚡ 响应快 | 混合检索优先召回高相关候选，缩短推荐路径 |
| 🧠 懂语义 | 结合向量语义匹配与关键词匹配，减少“搜不到/搜不准” |
| 📝 可解释 | 每条推荐都带理由，便于前端展示与运营复核 |
| 🔧 易集成 | 提供简洁 REST API，可直接接入 Web/App/小程序 |

## 🚀 30 秒上手

```bash
# 1) 启动 Elasticsearch
docker run -d --name es-rag -p 9200:9200 -e "discovery.type=single-node" -e "xpack.security.enabled=false" elastic/elasticsearch:8.17.0

# 2) 配置密钥
export OPENAI_API_KEY="your-api-key"

# 3) 启动服务
mvn spring-boot:run
```

启动后即可调用：`POST http://localhost:8080/api/v1/recommendations`

## ✨ 项目能力

- 🗣️ 自然语言商品推荐（中文场景）
- 🧠 混合检索：向量检索 + BM25 加权
- 🥇 可选重排（Jina reranker，可开关）
- 🎯 支持价格、分类、品牌、标签、库存过滤
- 🏗️ 启动时自动创建索引，并在索引为空时自动导入 `products.json`
- 🌐 自带简易 Web 页面：`/`

## 🧱 技术栈

- Java 17
- Spring Boot 3.3.6
- LangChain4j 0.36.2
- Elasticsearch Java Client 8.17.0
- Maven 3.6+

## 📁 目录结构

```text
rag-product-recommendation-service
├── pom.xml
├── src/main/java/com/example/rag
│   ├── RagApplication.java
│   ├── config
│   ├── controller
│   │   ├── RecommendationController.java
│   │   └── HealthController.java
│   ├── dto
│   ├── model
│   └── service
├── src/main/resources
│   ├── application.yml
│   ├── application-dev.yml
│   ├── data/products.json
│   ├── mapping/elasticsearch-mapping.json
│   └── static
│       ├── index.html
│       └── js/app.js
└── README.md
```

## 🚀 运行前准备

### 1. 启动 Elasticsearch 8.x 🔍

示例（本地 Docker）：

```bash
docker run -d \
  --name es-rag \
  -p 9200:9200 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  elastic/elasticsearch:8.17.0
```

### 2. 安装 IK 分词插件（必须）🧩

索引 mapping 使用了 `ik_max_word` / `ik_smart` 分词器，未安装会导致索引创建失败。  
请确保你的 Elasticsearch 节点已安装对应版本的 IK 插件。

### 3. 配置环境变量（推荐）🔐

建议使用环境变量而不是把密钥写入配置文件：

```bash
export OPENAI_API_KEY="your-api-key"
export OPENAI_API_URL="https://api.openai.com"
export ES_USERNAME="elastic"
export ES_PASSWORD=""
export JINA_API_KEY=""
```

## ⚙️ 配置说明

默认配置在 `src/main/resources/application.yml`：

- 应用端口：`8080`
- 默认 profile：`dev`（即会加载 `application-dev.yml`）
- Elasticsearch：`spring.elasticsearch.*`
- 模型配置：`openai.api.*`
- 推荐配置：`rag.recommendation.*`

常见配置项：

```yaml
openai:
  api:
    key: ${OPENAI_API_KEY:}
    base-url: ${OPENAI_API_URL:https://api.openai.com}
    embedding:
      model: text-embedding-3-small
      dimensions: 1024
    llm:
      model: gpt-4o-mini

rag:
  recommendation:
    default-top-k: 10
    max-top-k: 50
    search:
      hybrid-weight-vector: 0.7
      hybrid-weight-bm25: 0.3
    rerank:
      enabled: false
      provider: jina
      model: jina-reranker-v2-base-multilingual
      api-key: ${JINA_API_KEY:}
```

## ▶️ 启动方式

```bash
# 1) 使用 Java 17
java -version

# 2) 启动服务
mvn spring-boot:run
```

启动后访问：

- 🖥️ Web 页面：`http://localhost:8080/`
- 🤖 推荐接口：`POST http://localhost:8080/api/v1/recommendations`
- ❤️ 健康检查：`GET http://localhost:8080/actuator/health`
- ℹ️ 服务信息：`GET http://localhost:8080/actuator/info`

## 🗂️ 数据初始化机制

启动流程中会执行：

1. 🔎 检查索引 `test_products` 是否存在，不存在则自动创建。
2. 📦 检查索引文档数，若为 0，则读取 `classpath:data/products.json` 批量写入。
3. ⚡ 批量写入后异步生成 embedding 并回写文档。

## 📡 API 示例

### 1) 推荐商品 🤖

`POST /api/v1/recommendations`

请求示例：

```json
{
  "query": "适合夏天跑步的轻便运动鞋，预算500以内",
  "topK": 10,
  "filters": {
    "minPrice": 100,
    "maxPrice": 500,
    "categories": ["鞋类"],
    "brands": ["Nike", "李宁"],
    "tags": ["跑步", "透气"],
    "inStock": true
  }
}
```

响应示例：

```json
{
  "query": "适合夏天跑步的轻便运动鞋，预算500以内",
  "recommendations": [
    {
      "id": "1001",
      "name": "Nike Pegasus 40",
      "description": "轻便透气跑步鞋",
      "category": "鞋类",
      "price": 499.0,
      "brand": "Nike",
      "reason": "轻便透气，适合夏季跑步",
      "score": null,
      "imageUrl": "images/products/img_1.png"
    }
  ],
  "totalResults": 1,
  "processingTimeMs": 850,
  "modelUsed": "gpt-4o-mini",
  "timestamp": "2026-03-13T13:00:00"
}
```

### 2) 健康检查 ❤️

`GET /actuator/health`

会返回应用、embedding 模型、chat 模型的状态和响应时间。

## ⚠️ 已知限制

- 当前仅暴露推荐与健康检查接口，没有商品管理 REST 接口。
- `RecommendationService#recommendStream` 仍是占位实现，未提供真实 SSE 流式接口。
- 仓库当前未包含自动化测试用例（`src/test` 为空）。

## 🛠️ 故障排查

### Elasticsearch 索引创建失败并提示 IK tokenizer 🚫

原因：ES 未安装 IK 插件。  
处理：安装与 ES 版本匹配的 IK 插件后重启 ES。

### 推荐接口报模型调用错误（401/403/timeout）❌

检查：

- 🔑 `OPENAI_API_KEY` 是否有效
- 🔗 `OPENAI_API_URL` 是否与服务商兼容（OpenAI 或兼容网关）
- ⏱️ 网络连通性和超时配置（`openai.api.timeout`）

### 无推荐结果 🤔

检查：

- 📚 索引是否有数据：查看启动日志是否完成初始导入
- 🎛️ 过滤条件是否过严（如价格区间、品牌、库存）

## 🔒 安全说明

`application-dev.yml` 常用于本地开发，不建议提交真实密钥和密码。  
建议统一使用环境变量注入敏感信息，并在仓库中只保留占位符配置。
