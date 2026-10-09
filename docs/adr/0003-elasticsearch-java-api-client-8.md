---
status: accepted
---

# 采用 Elasticsearch Java API Client 8.x

客户端选用官方现行标准 `co.elastic.clients:elasticsearch-java`（8.x 线），不使用已弃用的 `RestHighLevelClient`（Easy-ES 的底层选型）。服务端版本锚定 8.x，与客户端同代；ES 9.x 及其客户端不在一期范围。
