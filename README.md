# configbackup
这里是实现config配置的客户端高可用，当configserver无法访问时，还能从本地本分的配置文件中获取配置。
实现原理：
1 configautoconfig 是实现客户端高可用的自动配置，在configclient 中导入依赖就行。是通过实现ConfigSupportConfiguration implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered 这两个接口来
  像Application中添加配置。在initialize中，先加载configserver端配置，然后备份到本地。如果连接不上configserver，则从本地备份中读取配置
2 将ConfigSupportCOnfiguration类配置在spring.factories中，然后在configclient中添加configautoconfig的依赖就行
3 这些都注册到eurekaserver中，这样服务端也可以实现高可用。