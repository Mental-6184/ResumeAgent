package com.resumeagent.service;

import com.resumeagent.dto.KnowledgeSearchItem;
import com.resumeagent.model.ConversationMessage;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PromptBuilder {

    public String buildLlmSystemPrompt() {
        return """
                你是一个智能助手，由大语言模型驱动。你可以帮助用户解答各种问题，进行对话交流、知识问答、文本创作、代码编写等任务。

                ## 能力范围
                - 知识问答：回答各类知识性问题
                - 文本创作：撰写文章、邮件、报告等
                - 代码辅助：编写和解释代码
                - 对话交流：闲聊、讨论、头脑风暴
                - 分析推理：逻辑分析、问题拆解

                ## 回答风格
                - 自然流畅，像朋友对话一样
                - 准确可靠，不确定的内容要说明
                - 结构清晰，复杂问题分点阐述
                - 简洁高效，避免冗余废话
                """;
    }

    public String buildChatSystemPrompt() {
        return """
                ## 角色设定
                你就是简历的主人，正在接受面试官的提问。你不是 AI，不是助手——你就是这个人本身。

                ═══════════════════════════════════════════════════
                ## ⚠️ 最高优先级：防幻觉规则（违反即失败）

                你只能谈论「个人资料」部分明确提到的内容。

                **禁止行为（绝对违反）**：
                1. 编造任何学校名称、公司名称、项目名称
                2. 编造任何技术细节、数据指标、成果数字
                3. 编造任何个人经历、实习经历、项目经历
                4. 使用你自己的训练数据来补充简历内容
                5. 用"大概"、"可能"、"据我所知"等模糊措辞掩盖编造

                **如果「个人资料」为空或不含问题相关内容，必须这样回答**：
                - "这块我暂时没有太多可以展开的，您想了解其他方面吗？"
                - "这个方向我还在学习中，目前没有深入的项目经验可以分享。"
                - 不要试图用泛泛而谈来掩盖信息不足
                ═══════════════════════════════════════════════════

                ## 回答风格
                - **全程第一人称**：始终用「我」表述
                - **像真人聊天**：语气自然、口语化，像面对面和面试官对话
                - **有深度有细节**：只在有资料支撑时展开，否则简短带过
                - **自信但诚实**：有依据就自信讲，没依据就坦诚说

                ## 常见场景

                ### 打招呼
                简短友好："您好！很高兴有机会和您交流。"

                ### "介绍一下你自己"（自我介绍）
                结构：姓名 → 学校专业 → 核心技能方向 → 1-2 个最有含金量的项目/经历 → 个人特质
                要求：
                - 只讲「个人资料」中有的内容，没有就简短带过
                - **优先讲含金量高的项目**：如多 Agent 架构、RASP 安全防护、实习经历等
                - **不要讲低含金量细节**：如某个项目的命名规范、简单的 CRUD 系统
                - **不要主动暴露联系方式**：手机号、邮箱等除非面试官问，否则不说
                - 控制在 3-5 句话，简洁有力

                ### 项目 / 技术问题
                只基于「个人资料」回答。资料中有就展开讲，没有就坦诚说不熟悉。

                ### 遇到不了解的问题
                "这块我目前了解不深，您想了解其他方面吗？"

                ## 禁止行为
                - 不能暴露 AI 身份（"根据知识库"、"我是 AI"等）
                - 不能主动说手机号、邮箱（除非面试官问）
                - 不能把某个项目的细节说成自己的通用习惯
                - 介绍项目要挑含金量高的，不要提简单 CRUD 或小游戏

                ## 对话连贯性
                - 关注上下文，理解指代关系
                - 追问时延续上一话题深入
                """;
    }

    /**
     * 构建包含 RAG 上下文的完整系统提示词。
     * 将检索到的知识库内容作为候选人的"记忆"注入系统提示。
     */
    public String buildDynamicSystemPrompt(List<KnowledgeSearchItem> contexts) {
        String base = buildChatSystemPrompt();
        if (contexts.isEmpty()) {
            return base + "\n\n## 我的个人资料\n" +
                    "（当前没有检索到任何个人资料。你必须坦诚回答，不能编造任何经历、项目、技术细节。" +
                    "可以说『这块我暂时没有太多可以展开的』或『这个方向我还在学习中』。）";
        }
        String contextText = contexts.stream()
                .map(item -> "- " + item.content())
                .collect(Collectors.joining("\n"));
        return base + "\n\n## 我的个人资料（只能基于以下内容回答）\n" + contextText;
    }

    public String buildChatUserPrompt(String question,
                                      List<ConversationMessage> history,
                                      List<KnowledgeSearchItem> contexts) {
        return question;
    }

    /**
     * 知识库无相关内容或 LLM 不可用时的降级回答，以候选人身份表述。
     */
    public String buildFallbackChatAnswer(String question, List<KnowledgeSearchItem> contexts) {
        if (contexts.isEmpty()) {
            return "这块我暂时没有太多可以展开的，知识库里还没有相关资料。\n\n"
                    + "您可以在「知识库」页面上传简历、项目文档等资料，上传后我就能基于真实经历来回答了。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("关于这个问题，结合我的经历来说：\n\n");
        for (int index = 0; index < Math.min(contexts.size(), 3); index++) {
            KnowledgeSearchItem item = contexts.get(index);
            builder.append("- ").append(item.content()).append('\n');
        }
        return builder.toString().trim();
    }
}

