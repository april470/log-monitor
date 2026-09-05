package com.example.logmonitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.logmonitor.dto.LogQueryDTO;
import com.example.logmonitor.entity.InterfaceLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface InterfaceLogMapper extends BaseMapper<InterfaceLog> {

    @Deprecated
    @Select("SELECT * FROM interface_log ORDER BY create_time DESC LIMIT 100")
    List<InterfaceLog> findLatest100();

    @Select("SELECT DATE(create_time) AS day, COUNT(*) AS count, AVG(cost_time) AS avgCost " +
            "FROM interface_log WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
            "GROUP BY DATE(create_time) ORDER BY day ASC")
    List<Map<String, Object>> findStatsForLast7Days();

    List<Map<String, Object>> countByStatus(@Param("queryDTO") LogQueryDTO queryDTO);

    Long countSlow(@Param("queryDTO") LogQueryDTO queryDTO);

    IPage<InterfaceLog> selectPage(IPage<InterfaceLog> page, @Param("queryDTO") LogQueryDTO queryDTO);
}