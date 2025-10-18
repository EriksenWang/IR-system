# IR-system

本项目是依据1000份专业文献pdf构建的搜索系统，使用grobid将pdf文档转换为json，以spring框架为基础，使用lucene创建索引，html+css+javascript编写前端，实现如下功能：

1. 按字段普通检索
2. 布尔检索
3. 短语检索
4. 高级检索（类似知网）
5. 结果翻页
6. 搜索历史保存
7. 结果跳转至原pdf


## 启用 grobid Docker

```bash
docker run -t --rm -p 8070:8070 lfoppiano/grobid:0.8.2
```

## 目录结构

```
.
├── process_pdf.py       # 批量解析PDF为XML的主程序
├── requirements.txt     # Python依赖包列表
├── README.md            # 项目说明文档
├── config.json          # grobid_client配置文件
├── output               # 文档解析后生成的json文件目录
├── new_index            # lucene索引存储位置
├── demo\src\main\java\com\example
    ├──App.java          # spring启动
    ├──Controller.java   # 后端调用索引
    ├──newIndexer.java   # 生成索引
    └──PdfResourceConfig.java
├── demo\src\main\resources
    ├── static
        ├──images        # 图片
        ├──advances.html # 高级索引页面
        └──index.html    # 初始页面
    ├── application.properties
├──
└── ...

```


## 项目启动

在运行App.java之后，转到：

```
http://localhost:8080/
```

## 原数据

[pdf数据](https://pan.baidu.com/share/init?surl=bIFRaCEsNmqDfokn7JRYkw)

