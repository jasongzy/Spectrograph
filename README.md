# Spectrograph

配合相关光学器件可以实现苹果糖度无损测量

## 使用步骤

1. 点击（或长按）*相机悬浮按钮*，拍摄（或从相册选择）一张照片作为「光源光谱」图像，APP 将自动绘制该图像的**光谱曲线**；

2. 点击光谱曲线图，设置当前数据为「光源光谱」（参照），此后*图像对比悬浮按钮* 将变为可见；

3. 再次点击（或长按）*相机悬浮按钮*，选取一张加入样品后的光谱图像，APP 将自动绘制该图像的**光谱曲线**；

4. 点击*图像对比悬浮按钮*，APP 将绘制「当前光谱」相对于「光源光谱」的**吸光度曲线**，同时计算并显示出糖度测量结果。

## NOTE

- 绘制吸光度曲线所用的两张图片必须保证**像素宽度完全一致**！

- 绘制吸光度曲线后再次点击*图像对比悬浮按钮* 可以切换显示「当前图像光谱曲线」/「吸光度曲线」

- 点击吸光度曲线可以将当前吸光度数据导出到 `/storage/emulated/0/Android/data/top.jasongzy.spectrograph/files/AData/<当前图片文件名>.txt`

- 右上角选项可以清除照片拍摄缓存并恢复初始布局
