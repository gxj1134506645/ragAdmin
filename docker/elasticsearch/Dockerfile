FROM docker.elastic.co/elasticsearch/elasticsearch:8.17.3

# 预装 IK 中文分词器，避免手工进容器安装；镜像重建后仍可稳定复用。
RUN /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch \
    https://get.infini.cloud/elasticsearch/analysis-ik/8.17.3

