package me.codeleep.victor.core.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import me.codeleep.victor.core.dto.MetadataQueryRequest;
import me.codeleep.victor.core.dto.MetadataRequest;
import me.codeleep.victor.core.dto.MetadataVO;
import me.codeleep.victor.core.entity.Metadata;

import java.util.List;

/**
 * 元数据服务接口
 */
public interface MetadataService extends IService<Metadata> {

    /**
     * 根据分类获取元数据列表
     * @param category 分类
     * @return 元数据列表
     */
    List<MetadataVO> getByCategory(String category);

    /**
     * 获取所有分类
     * @return 分类列表
     */
    List<String> getAllCategories();

    /**
     * 分页查询元数据
     * @param request 查询请求
     * @return 分页结果
     */
    Page<MetadataVO> list(MetadataQueryRequest request);

    /**
     * 创建元数据
     * @param request 请求
     * @return 元数据
     */
    MetadataVO create(MetadataRequest request);

    /**
     * 更新元数据
     * @param id ID
     * @param request 请求
     * @return 元数据
     */
    MetadataVO update(Long id, MetadataRequest request);

    /**
     * 删除元数据
     * @param id ID
     */
    void delete(Long id);

    /**
     * 根据ID获取元数据详情
     * @param id ID
     * @return 元数据
     */
    MetadataVO getDetail(Long id);
}
