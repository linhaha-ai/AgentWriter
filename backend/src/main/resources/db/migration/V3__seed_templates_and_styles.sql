-- ============================================================
-- V3: 修复 writer_style.created_at 默认值 + 插入种子数据
-- ============================================================

-- 1. 为已有的 created_at 列补充 DEFAULT NOW()
ALTER TABLE writer_style ALTER COLUMN created_at SET DEFAULT NOW();
ALTER TABLE article_template ALTER COLUMN created_at SET DEFAULT NOW();
ALTER TABLE article_template ALTER COLUMN updated_at SET DEFAULT NOW();

-- 2. 插入 5 种预设写作风格（如果表为空）
INSERT INTO writer_style (name, description, tone_prompt, features, enabled)
SELECT * FROM (VALUES
    (
        '技术深度型',
        '深入原理、代码示例丰富、适合技术人群',
        '你是一位资深技术专家。请采用技术深度型的写作风格：\n- 深入讲解技术原理和实现细节\n- 包含丰富的代码示例和最佳实践\n- 使用准确的技术术语，不需要过度简化\n- 适合有一定技术基础的读者阅读\n- 可以讨论架构设计、性能优化等进阶话题',
        '技术深度,原理分析,代码示例',
        TRUE
    ),
    (
        '通俗易懂型',
        '类比解释、少用专业术语、适合入门读者',
        '你是一位善于讲解的科普作者。请采用通俗易懂型的写作风格：\n- 多用比喻和类比来解释复杂概念\n- 尽量减少专业术语的使用，必须使用时给出解释\n- 句子简短明了，段落结构清晰\n- 适合技术入门读者和转型学习者\n- 保持友好、鼓励的语气',
        '通俗易懂,类比解释,入门友好',
        TRUE
    ),
    (
        '故事叙事型',
        '以故事/案例引入、情感共鸣强、适合大众传播',
        '你是一位讲故事的高手。请采用故事叙事型的写作风格：\n- 以真实案例、故事或场景引入主题\n- 注重情感共鸣和代入感\n- 使用生动的语言和形象的描述\n- 结构上按照"引出问题→展开分析→给出结论"的叙事逻辑\n- 适合大众传播和社交媒体分享',
        '故事叙事,案例引入,情感共鸣',
        TRUE
    ),
    (
        '资讯快报型',
        '简洁干练、重点突出、适合快速阅读',
        '你是一位新闻编辑。请采用资讯快报型的写作风格：\n- 开篇即点明核心信息和结论\n- 内容简洁干练，重点突出\n- 使用小标题和要点列表方便快速浏览\n- 每段控制在 3-5 句以内\n- 适合需要快速获取信息的读者',
        '资讯快报,简洁干练,重点突出',
        TRUE
    ),
    (
        '观点评论型',
        '立场鲜明、批判性分析、适合行业讨论',
        '你是一位有态度的行业评论员。请采用观点评论型的写作风格：\n- 开篇即表明鲜明的立场和观点\n- 进行批判性分析，不只停留在表面\n- 引用事实和数据支撑论点\n- 预测趋势并提出建设性建议\n- 适合引发行业讨论和深度思考',
        '观点评论,批判性分析,行业讨论',
        TRUE
    )
) AS t(name, description, tone_prompt, features, enabled)
WHERE NOT EXISTS (SELECT 1 FROM writer_style);

-- 3. 插入 8 种常用文章模板（如果表为空）
INSERT INTO article_template (name, description, title_template, outline_template, cover_rule, enabled)
SELECT * FROM (VALUES
    (
        '技术教程',
        'Step-by-step 技术教程，适合教读者如何实现某个功能',
        '{topic}：从入门到实践',
        '[
            {"title":"背景介绍","description":"介绍技术出现的背景、解决什么问题"},
            {"title":"环境准备","description":"列出前置依赖、版本要求、安装步骤"},
            {"title":"快速上手","description":"一个最小可运行的示例，让读者先跑起来"},
            {"title":"核心概念","description":"讲解关键概念、架构和设计思路"},
            {"title":"实战演练","description":"完整的项目实战，分步骤讲解"},
            {"title":"常见问题","description":"列出常见坑点和解决方案"},
            {"title":"总结","description":"回顾要点，推荐进一步学习资源"}
        ]',
        'technology',
        TRUE
    ),
    (
        '深度分析',
        '深入技术原理、源码分析、架构解读类文章',
        '深入理解 {topic}：原理与实践',
        '[
            {"title":"引言","description":"提出问题，说明为什么要深入分析"},
            {"title":"整体架构","description":"系统整体架构图、模块划分"},
            {"title":"核心原理","description":"深入讲解核心机制和设计思想"},
            {"title":"源码解读","description":"关键源码片段分析，解释为什么这样设计"},
            {"title":"性能与对比","description":"性能基准测试、与其他方案对比"},
            {"title":"最佳实践","description":"生产环境中的最佳实践和注意事项"},
            {"title":"总结与展望","description":"总结核心要点，展望未来发展方向"}
        ]',
        'deep-dive',
        TRUE
    ),
    (
        '新闻解读',
        '结合热点新闻进行技术解读和分析',
        '解读 | {topic}',
        '[
            {"title":"事件回顾","description":"简要回顾新闻事件的核心内容"},
            {"title":"这意味着什么","description":"分析该事件对行业/技术圈的影响"},
            {"title":"技术角度解读","description":"从技术层面深入分析事件的本质"},
            {"title":"各方观点","description":"汇总业内专家的不同观点和看法"},
            {"title":"未来影响","description":"预测该事件可能带来的长期影响"},
            {"title":"小结","description":"总结核心观点，给出自己的判断"}
        ]',
        'news',
        TRUE
    ),
    (
        '最佳实践',
        '分享生产环境中的最佳实践、踩坑经验和优化方案',
        '{topic} 最佳实践指南',
        '[
            {"title":"前言","description":"说明为什么要关注这方面的最佳实践"},
            {"title":"常见误区","description":"列出开发中容易踩的坑和错误做法"},
            {"title":"推荐方案","description":"给出经过验证的推荐方案和实现方式"},
            {"title":"代码示例","description":"提供完整的代码示例和配置说明"},
            {"title":"性能考量","description":"性能优化方面的建议和基准数据"},
            {"title":"安全建议","description":"安全方面的最佳实践和注意事项"},
            {"title":"总结","description":"核心要点速查清单"}
        ]',
        'best-practice',
        TRUE
    ),
    (
        '对比评测',
        '技术选型对比、工具评测、方案优劣分析',
        '{topic} 对比：哪个更适合你？',
        '[
            {"title":"选型背景","description":"为什么需要对比、适用场景说明"},
            {"title":"参评选手","description":"介绍参与对比的各个方案/工具"},
            {"title":"对比维度","description":"列出对比的各个维度（性能、生态、学习曲线等）"},
            {"title":"详细对比","description":"各维度逐一对比，辅以数据和图表"},
            {"title":"优缺点总结","description":"每个方案的优缺点汇总"},
            {"title":"选型建议","description":"针对不同场景给出推荐方案"},
            {"title":"结论","description":"最终结论和决策建议"}
        ]',
        'comparison',
        TRUE
    ),
    (
        '入门指南',
        '面向新手的入门教程，零基础也能看懂',
        '{topic} 入门指南：零基础也能上手',
        '[
            {"title":"什么是 {topic}","description":"用通俗语言解释是什么、能做什么"},
            {"title":"为什么学习","description":"学习它能带来什么价值"},
            {"title":"安装与配置","description":"详细的安装步骤和环境配置"},
            {"title":"第一个示例","description":"从零开始完成第一个示例"},
            {"title":"核心概念速览","description":"快速了解最核心的概念和术语"},
            {"title":"常用操作","description":"日常开发中最常用的操作和命令"},
            {"title":"下一步","description":"进阶学习路线和推荐资源"}
        ]',
        'beginner',
        TRUE
    ),
    (
        '问题解决',
        '针对特定问题提供完整排查和解决方案',
        '如何解决 {topic}？完整排查指南',
        '[
            {"title":"问题描述","description":"清晰地描述问题和复现步骤"},
            {"title":"常见原因","description":"列出可能导致该问题的常见原因"},
            {"title":"排查思路","description":"系统性的排查步骤和思路"},
            {"title":"解决方案","description":"针对每种原因的详细解决方案"},
            {"title":"预防措施","description":"如何避免类似问题再次发生"},
            {"title":"参考资料","description":"相关文档和参考资料链接"}
        ]',
        'troubleshooting',
        TRUE
    ),
    (
        '趋势展望',
        '技术趋势分析、行业预测、未来展望',
        '{topic} 趋势展望：未来何去何从',
        '[
            {"title":"现状回顾","description":"回顾当前的发展阶段和现状"},
            {"title":"驱动力分析","description":"分析推动发展的关键因素"},
            {"title":"趋势一","description":"第一个重要趋势及依据"},
            {"title":"趋势二","description":"第二个重要趋势及依据"},
            {"title":"趋势三","description":"第三个重要趋势及依据"},
            {"title":"挑战与机遇","description":"面临的挑战和潜在的机遇"},
            {"title":"行动建议","description":"给读者/从业者的建议"},
            {"title":"总结","description":"核心观点总结"}
        ]',
        'trend',
        TRUE
    )
) AS t(name, description, title_template, outline_template, cover_rule, enabled)
WHERE NOT EXISTS (SELECT 1 FROM article_template);
