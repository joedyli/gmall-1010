package com.atguigu.gmall.item.service;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.item.feign.GmallPmsClient;
import com.atguigu.gmall.item.feign.GmallSmsClient;
import com.atguigu.gmall.item.feign.GmallWmsClient;
import com.atguigu.gmall.item.vo.ItemVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ItemService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    public ItemVo load(Long skuId) {

        ItemVo itemVo = new ItemVo();

        // 根据skuId查询sku的信息1
        CompletableFuture<SkuEntity> skuCompletableFuture = CompletableFuture.supplyAsync(() -> {
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(skuId);
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {
                return null;
            }
            itemVo.setSkuId(skuId);
            itemVo.setTitle(skuEntity.getTitle());
            itemVo.setSubTitle(skuEntity.getSubtitle());
            itemVo.setPrice(skuEntity.getPrice());
            itemVo.setWeight(skuEntity.getWeight());
            itemVo.setDefaltImage(skuEntity.getDefaultImage());
            return skuEntity;
        }, threadPoolExecutor);

        // 根据cid3查询分类信息2
        CompletableFuture<Void> categoryCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<CategoryEntity>> categoryResponseVo = this.pmsClient.queryCategoriesByCid3(skuEntity.getCategoryId());
            List<CategoryEntity> categoryEntities = categoryResponseVo.getData();
            itemVo.setCategories(categoryEntities);
        }, threadPoolExecutor);

        // 根据品牌的id查询品牌3
        CompletableFuture<Void> brandCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
            BrandEntity brandEntity = brandEntityResponseVo.getData();
            if (brandEntity != null) {
                itemVo.setBrandId(brandEntity.getId());
                itemVo.setBrandName(brandEntity.getName());
            }
        }, threadPoolExecutor);

        // 根据spuId查询spu4
        CompletableFuture<Void> spuCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
            SpuEntity spuEntity = spuEntityResponseVo.getData();
            if (spuEntity != null) {
                itemVo.setSpuId(spuEntity.getId());
                itemVo.setSpuName(spuEntity.getName());
            }
        }, threadPoolExecutor);

        // 跟据skuId查询图片5
        CompletableFuture<Void> skuImagesCompletableFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<SkuImagesEntity>> skuImagesResponseVo = this.pmsClient.queryImagesBySkuId(skuId);
            List<SkuImagesEntity> skuImagesEntities = skuImagesResponseVo.getData();
            itemVo.setImages(skuImagesEntities);
        }, threadPoolExecutor);

        // 根据skuId查询sku营销信息6
        CompletableFuture<Void> salesCompletableFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.querySalesBySkuId(skuId);
            List<ItemSaleVo> sales = salesResponseVo.getData();
            itemVo.setSales(sales);
        }, threadPoolExecutor);

        // 根据skuId查询sku的库存信息7
        CompletableFuture<Void> storeCompletableFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<WareSkuEntity>> wareSkuResponseVo = this.wmsClient.queryWareSkusBySkuId(skuId);
            List<WareSkuEntity> wareSkuEntities = wareSkuResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                itemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }
        }, threadPoolExecutor);

        // 根据spuId查询spu下的所有sku的销售属性
        CompletableFuture<Void> saleAttrsCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<SaleAttrValueVo>> saleAttrValueVoResponseVo = this.pmsClient.querySkuAttrValuesBySpuId(skuEntity.getSpuId());
            List<SaleAttrValueVo> saleAttrValueVos = saleAttrValueVoResponseVo.getData();
            itemVo.setSaleAttrs(saleAttrValueVos);
        }, threadPoolExecutor);

        // 当前sku的销售属性
        CompletableFuture<Void> saleAttrCompletableFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySkuAttrValuesBySkuId(skuId);
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
            Map<Long, String> map = skuAttrValueEntities.stream().collect(Collectors.toMap(SkuAttrValueEntity::getAttrId, SkuAttrValueEntity::getAttrValue));
            itemVo.setSaleAttr(map);
        }, threadPoolExecutor);

        // 根据spuId查询spu下的所有sku及销售属性的映射关系
        CompletableFuture<Void> skusJsonCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<String> skusJsonResponseVo = this.pmsClient.querySkusJsonBySpuId(skuEntity.getSpuId());
            String skusJson = skusJsonResponseVo.getData();
            itemVo.setSkusJson(skusJson);
        }, threadPoolExecutor);

        // 根据spuId查询spu的海报信息9
        CompletableFuture<Void> spuImagesCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<SpuDescEntity> spuDescEntityResponseVo = this.pmsClient.querySpuDescById(skuEntity.getSpuId());
            SpuDescEntity spuDescEntity = spuDescEntityResponseVo.getData();
            if (spuDescEntity != null && StringUtils.isNotBlank(spuDescEntity.getDecript())) {
                String[] images = StringUtils.split(spuDescEntity.getDecript(), ",");
                itemVo.setSpuImages(Arrays.asList(images));
            }
        }, threadPoolExecutor);

        // 根据cid3 spuId skuId查询组及组下的规格参数及值 10
        CompletableFuture<Void> groupCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<ItemGroupVo>> groupResponseVo = this.pmsClient.queryGoupsWithAttrValues(skuEntity.getCategoryId(), skuEntity.getSpuId(), skuId);
            List<ItemGroupVo> itemGroupVos = groupResponseVo.getData();
            itemVo.setGroups(itemGroupVos);
        }, threadPoolExecutor);

        CompletableFuture.allOf(categoryCompletableFuture, brandCompletableFuture, spuCompletableFuture,
                skuImagesCompletableFuture, salesCompletableFuture, storeCompletableFuture, saleAttrsCompletableFuture,
                saleAttrCompletableFuture, skusJsonCompletableFuture, spuImagesCompletableFuture, groupCompletableFuture).join();

        return itemVo;
    }

//    public static void main(String[] args) throws ExecutionException, InterruptedException {
//
////        CompletableFuture.runAsync(() -> {
////            System.out.println("初始化CompletableFuture子任务：runAsync");
////        });
//        CompletableFuture<String> afuture = CompletableFuture.supplyAsync(() -> {
//            System.out.println("初始化CompletableFuture子任务：supplyAsync");
//            System.out.println("=============end================");
////            int i = 1/0;
//            return "hello CompletableFuture!";
//        });
//        CompletableFuture<String> bfuture = afuture.thenApplyAsync(t -> {
//            System.out.println("====================thenApplyAsync=========================");
//            System.out.println("上一个任务的返回结果：" + t);
//            try {
//                TimeUnit.SECONDS.sleep(1);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            System.out.println("====================thenApplyAsync end=========================");
//            return "hello thenApplyAsync";
//        });
//        CompletableFuture<String> cfuture = afuture.thenApplyAsync(t -> {
//            System.out.println("====================thenApplyAsync2=========================");
//            System.out.println("上一个任务的返回结果：" + t);
//            try {
//                TimeUnit.SECONDS.sleep(1);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            System.out.println("====================thenApplyAsync2 end=========================");
//            return "hello thenApplyAsync2";
//        });
//        CompletableFuture<Void> dfuture = afuture.thenAcceptAsync(t -> {
//            System.out.println("====================thenAcceptAsync=========================");
//            System.out.println("上一个任务的返回结果：" + t);
//            try {
//                TimeUnit.SECONDS.sleep(1);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            System.out.println("====================thenAcceptAsync end=========================");
//        });
//        CompletableFuture.allOf(bfuture, cfuture, dfuture).join();
//        System.out.println("主线程输出。。。。。。。。。。相当于主线程return");
//        try {
//            System.in.read();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

//                .whenComplete((t, u) -> {
//            System.out.println("====================whenComplete=========================");
//            System.out.println("上一个任务的返回值t：" + t);
//            System.out.println("上一个任务的异常信息u：" + u);
//            System.out.println("====================whenComplete end=========================");
//        }).exceptionally(t -> {
//            System.out.println("====================exceptionally=========================");
//            System.out.println("上一个任务的异常信息t: " + t);
//            System.out.println("====================exceptionally end=========================");
//            return "hello exceptionally!";
//        });

//        new MyThread().start();
//        new Thread(new MyRunnable()).start();
//        FutureTask futureTask = new FutureTask<>(new MyCallable());
//        new Thread(futureTask).start();
//        System.out.println(futureTask.get());

//        ExecutorService executorService = Executors.newFixedThreadPool(3);
//        executorService.execute(()-> {
//            System.out.println("这是线程池工具类的方式初始化子线程程序");
//        });
//        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(3, 5, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500));
//        threadPoolExecutor.execute(() -> {
//            System.out.println("这是线程池构造方法的形式初始化多线程程序");
//        });
//    }
}

//class MyCallable implements Callable<String> {
//    @Override
//    public String call() throws Exception {
//        System.out.println("这是Callable接口实现多线程程序");
//        return "callable";
//    }
//}
//
//class MyRunnable implements Runnable {
//    @Override
//    public void run() {
//        System.out.println("这是runnable接口的方式实现多线程程序");
//    }
//}
//
//class MyThread extends Thread{
//    @Override
//    public void run() {
//        System.out.println("这是thread类的方式实现多线程程序");
//    }
//}
