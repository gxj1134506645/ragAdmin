用户问题：${question}

当前回答：${answer}

当前上下文：
- referenceCount=${reference_count}

输出要求：
1. confidence 只能是 HIGH、MEDIUM、LOW
2. 如果 referenceCount=0，hasKnowledgeBaseEvidence 通常应为 false
3. 仅当回答存在不确定性、明显不足或建议继续核验时，needFollowUp 才为 true
4. 不要输出正文解释
