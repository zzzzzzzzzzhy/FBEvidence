package com.evidence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evidence.entity.GitBranch;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GitBranchMapper extends BaseMapper<GitBranch> {
}