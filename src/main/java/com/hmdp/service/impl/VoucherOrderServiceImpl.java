package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService secKillVoucherService;
    @Resource
    private RedisWorker redisWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    private final BlockingQueue<VoucherOrder> orderTask = new ArrayBlockingQueue<>(1024 * 1024);
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
    private IVoucherOrderService voucherOrderService;

    static {

        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @PostConstruct
    public void init() {
        EXECUTOR_SERVICE.submit(() -> {
            while (true) {
                try {
                    VoucherOrder voucherOrder = orderTask.take();

                        RLock redisLock = redissonClient.getLock("lock:order:" + voucherOrder.getUserId());
                    try {
                        boolean lock = redisLock.tryLock();
                        if (!lock) {
                            log.error("不可重复下单");
                            return;
                        }
                        voucherOrderService.creatVoucherOrder(voucherOrder);
                    }
                     finally {
                        redisLock.unlock();
                    }
                } catch (Exception e) {
                    log.error("处理订单错误", e);

                }
            }
        });

    }

    @Override
    public Result secKillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Arrays.asList(SECKILL_STOCK_KEY+voucherId,SECKILL_ORDER_KEY+voucherId),
                 userId.toString()
        );
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "您已购买");
        }

        long orderId = redisWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(orderId);
        orderTask.add(voucherOrder);
        voucherOrderService = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);

      /*  SeckillVoucher seckillVoucher = secKillVoucherService.getById(voucherId);
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        //判断时间是否过期
        if (beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("活动暂未开始");
        }
        //判断时间是否开始
        if (endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("活动已结束");
        }
        //检查库存
        if (seckillVoucher.getStock() < 1) {

            return Result.fail("今日优惠券已抢完");
        }
        Long id = UserHolder.getUser().getId();
        //RedisLock redisLock= new RedisLock("order"+id,stringRedisTemplate);
        RLock redisLock = redissonClient.getLock("lock:order:" + id);
        boolean lock = redisLock.tryLock();
        if (!lock) {
            return Result.fail("不可重复下单");
        }
        try {
            IVoucherOrderService voucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
            return voucherOrderService.creatVoucherOrder(voucherId);
        } finally {
            redisLock.unlock();
        }
*/
    }

    @Transactional
    public void creatVoucherOrder(VoucherOrder voucherOrder) {
        Long id = voucherOrder.getUserId();
        long count = query().eq("user_id", id).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            return;
        }
        boolean success = secKillVoucherService.update().setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            return;
        }


        save(voucherOrder);

    }
}
