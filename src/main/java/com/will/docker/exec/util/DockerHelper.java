package com.will.docker.exec.util;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;

/**
 * docker操作类.
 * @author will
 */
public class DockerHelper {
    public static void execute(String ip,DockerAction dockerAction)throws Exception{
        DockerClient docker = DefaultDockerClient.builder().uri("http://".concat(ip).concat(":2375")).apiVersion("v1.30").build();
        dockerAction.action(docker);
        docker.close();
    }

    public static <T> T query(String ip,DockerQuery<T> dockerQuery)throws Exception{
        DockerClient docker = DefaultDockerClient.builder().uri("http://".concat(ip).concat(":2375")).apiVersion("v1.30").build();
        T result=dockerQuery.action(docker);
        docker.close();
        return result;
    }

    public interface DockerAction {
        void action(DockerClient docker) throws Exception;
    }

    public interface DockerQuery<T> {
        T action(DockerClient docker) throws Exception;
    }
}
