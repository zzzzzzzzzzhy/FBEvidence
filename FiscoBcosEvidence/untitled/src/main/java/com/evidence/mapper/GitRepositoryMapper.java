package com.evidence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evidence.entity.GitRepository;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GitRepositoryMapper extends BaseMapper<GitRepository> {
}