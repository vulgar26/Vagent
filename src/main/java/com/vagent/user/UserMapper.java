package com.vagent.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper；复杂 SQL 可在此增加方法或配套 XML。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
