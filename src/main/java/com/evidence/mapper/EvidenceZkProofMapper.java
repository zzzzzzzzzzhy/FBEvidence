package com.evidence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evidence.entity.EvidenceZkProof;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EvidenceZkProofMapper extends BaseMapper<EvidenceZkProof> {

    @Select("SELECT * FROM evidence_zk_proofs WHERE evidence_id = #{evidenceId} ORDER BY id DESC LIMIT 1")
    EvidenceZkProof findLatestByEvidenceId(Long evidenceId);
}
