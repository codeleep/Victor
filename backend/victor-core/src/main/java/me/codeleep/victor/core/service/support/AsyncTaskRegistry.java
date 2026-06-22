package me.codeleep.victor.core.service.support;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异步任务在途登记表。
 * <p>
 * 用于状态驱动的自愈: 异步任务(出题/评估)进入"生成中"状态时登记在途,
 * 完成后注销。当读取到"生成中"状态但登记表中无记录时(如服务重启后线程丢失),
 * 可据此判断需要重新触发,避免状态卡死。
 * <p>
 * 仅适用于单实例部署; 多实例需改用分布式锁或数据库标记。
 */
@Component
public class AsyncTaskRegistry {

    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * 标记任务开始执行。
     *
     * @param id 任务关联的业务ID(配置ID/会话ID)
     * @return true 表示抢占成功(此前无在途任务), false 表示已有任务在执行(避免重复触发)
     */
    public boolean start(Long id) {
        return inFlight.add(id);
    }

    /**
     * 标记任务结束(无论成功或失败), 从在途表中移除。
     */
    public void finish(Long id) {
        inFlight.remove(id);
    }

    /**
     * 判断指定任务是否正在执行。
     */
    public boolean isRunning(Long id) {
        return inFlight.contains(id);
    }
}