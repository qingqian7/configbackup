package com.cloud.configautoconfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.bootstrap.config.PropertySourceBootstrapConfiguration;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

@Configuration
@EnableConfigurationProperties(ConfigSupportProperties.class)
public class ConfigSupportConfiguration implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {
    private final Logger logger = LoggerFactory.getLogger(ConfigSupportConfiguration.class);
    private final Integer orderNum = Ordered.HIGHEST_PRECEDENCE +11;

    @Autowired(required = false)
    private List<PropertySourceLocator> propertySourceLocators = Collections.emptyList();

    @Autowired
    private ConfigSupportProperties configSupportProperties;


    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        if(!isHasCloudConfigLocator(this.propertySourceLocators)){
            logger.info("未启用config server 管理配置");
            return;
        }
        logger.info("检查config server配置资源");
        ConfigurableEnvironment environment = configurableApplicationContext.getEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        logger.info("加载propertySource源：" + propertySources.size() + "个");
        if(!configSupportProperties.isEnable()){
            logger.warn("未启用配置备份功能呢，可使用{}.enable打开",ConfigSupportProperties.CONFIG_PREFIX);
            return;
        }

        if(isCloudConfigLoaded(propertySources)){
            PropertySource cloudConfigSource = getLoadedCloudPropertySource(propertySources);
            logger.info("成功获取configserver配置资源");
            //备份
            Map<String,Object> backupPropertyMap = makeBackupPropertyMap(cloudConfigSource);
            doBackup(backupPropertyMap,configSupportProperties.getFallbackLocation());
        }else{
            logger.error("获取configserver配置资源失败");
            Properties backupProperties = loadBackupProperty(configSupportProperties.getFallbackLocation());
            if(backupProperties != null){
                HashMap backupSourceMap = new HashMap<>(backupProperties);
                PropertySource backupSource = new MapPropertySource("backupSource",backupSourceMap);
                propertySources.addFirst(backupSource);
                logger.info("使用备份的配置启动：{}",configSupportProperties.getFallbackLocation());
            }
        }
    }

    @Override
    public int getOrder() {
        return orderNum;
    }
    /*
    是否启用了spring cloud config 获取配置资源
     */
    private boolean isHasCloudConfigLocator(List<PropertySourceLocator> propertySourceLocators){
        for(PropertySourceLocator locator : propertySourceLocators){
            if(locator instanceof ConfigServicePropertySourceLocator){
                return true;
            }
        }
        return false;
    }

    /*
    是否启用cloud config
     */
    private boolean isCloudConfigLoaded(MutablePropertySources propertySources){
        if(getLoadedCloudPropertySource(propertySources) == null){
            return false;
        }
        return true;
    }

    /*
    获取加载的cloud config的配置项
     */
    private PropertySource getLoadedCloudPropertySource(MutablePropertySources propertySources){
        if(!propertySources.contains(PropertySourceBootstrapConfiguration.BOOTSTRAP_PROPERTY_SOURCE_NAME)){
            return null;
        }
        PropertySource propertySource = (PropertySource) propertySources.get(PropertySourceBootstrapConfiguration.BOOTSTRAP_PROPERTY_SOURCE_NAME);
        if(propertySource instanceof CompositePropertySource){
            for(PropertySource<?> source : ((CompositePropertySource) propertySource).getPropertySources()){
                if(source.getName().equals("configService")){
                    return source;
                }
            }
        }
        return null;
    }

    /*
    生成备份数据
     */

    private Map<String,Object> makeBackupPropertyMap(PropertySource propertySource){
        Map<String, Object> backupSourceMap = new HashMap<>();
        if(propertySource instanceof CompositePropertySource){
            CompositePropertySource compositePropertySource = (CompositePropertySource) propertySource;
            for(PropertySource<?> source : compositePropertySource.getPropertySources()){
                if(source instanceof MapPropertySource){
                    MapPropertySource mapPropertySource = (MapPropertySource) source;
                    for(String propertyName : mapPropertySource.getPropertyNames()){
                        if(!backupSourceMap.containsKey(propertyName)){
                            backupSourceMap.put(propertyName,mapPropertySource.getProperty(propertyName));
                        }
                    }
                }
            }
        }
        return backupSourceMap;
    }

    /*
    生成备份文件
     */

    private void doBackup(Map<String,Object> backupPropertyMap,String filePath){
        FileSystemResource fileSystemResource = new FileSystemResource(filePath);
        File backupFile = fileSystemResource.getFile();
        try{
            if(!backupFile.exists()){
                backupFile.createNewFile();
            }
            if(!backupFile.canWrite()){
                logger.error("无法读写文件{}",fileSystemResource.getPath());
            }
            Properties properties = new Properties();
            Iterator<String> keyIterator = backupPropertyMap.keySet().iterator();
            while(keyIterator.hasNext()){
                String key = keyIterator.next();
                properties.setProperty(key,String.valueOf(backupPropertyMap.get(key)));
            }
            FileOutputStream fos = new FileOutputStream(fileSystemResource.getFile());
            properties.store(fos,"backup cloud config");
        } catch (IOException e) {
            logger.error("文件操作失败：{}",fileSystemResource.getPath());
            e.printStackTrace();
        }
    }

    /*
    加载本地文件
     */

    private Properties loadBackupProperty(String filePath){
        PropertiesFactoryBean propertiesFactoryBean = new PropertiesFactoryBean();
        Properties properties = new Properties();
        try{
            FileSystemResource fileSystemResource = new FileSystemResource(filePath);
            propertiesFactoryBean.setLocation(fileSystemResource);

            propertiesFactoryBean.afterPropertiesSet();
            properties = propertiesFactoryBean.getObject();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return properties;
    }
}
