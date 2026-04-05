package com.vagent.chat.message;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code messages} 表的 MyBatis-Plus Mapper。
 * <p>
 * 仅继承 {@link BaseMapper}，复杂条件查询放在 {@link MessageService} 里用 {@code Wrappers} 拼装，
 * 与 {@link com.vagent.conversation.ConversationMapper} 的写法保持一致。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
