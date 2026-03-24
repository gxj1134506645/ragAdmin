当前问题：${question}

当前场景：
- knowledgeBaseScene=${knowledge_base_scene}
- selectedKnowledgeBaseCount=${selected_knowledge_base_count}
- retrievalAvailable=${retrieval_available}
- webSearchAvailable=${web_search_available}

规划要求：
1. intent 使用简洁稳定的枚举风格命名
2. 如果 needRetrieval=true，则 retrievalQuery 必须是适合向量检索的短查询
3. 如果 needWebSearch=true，则 webSearchQuery 必须是适合联网搜索的短查询
4. 如果某项能力不需要或不可用，对应 query 返回空字符串
5. 不要直接回答用户问题
