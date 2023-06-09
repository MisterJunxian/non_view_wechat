package com.wechat.service;

import com.wechat.common.Message;
import com.wechat.common.MessageType;
import com.wechat.common.User;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务器，监听 9999 端口，保持通信
 */
public class WeChatServer {
    private ServerSocket serverSocket;
    /**
     * 存放合法用户的信息
     */
    public static ConcurrentHashMap<String, User> validUsers = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, ArrayList<Message>> offLineMessages = new ConcurrentHashMap<>();
    /**
     * 统一的时间格式
     */
    public static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // 在静态代码块初始化合法用户
    static {
        validUsers.put("001", new User("001", "666666"));
        validUsers.put("002", new User("002", "666666"));
        validUsers.put("003", new User("003", "666666"));
    }
    
    public static void main(String[] args) {
        new WeChatServer();
    }
    
    public WeChatServer() {
        try {
            System.out.println("等待客户端连接……");
            serverSocket = new ServerSocket(9999);
            new Thread(new SendNewsToAllClients()).start();
            
            // 连接到一个用户后应该继续监听
            while (true) {
                Socket socket = serverSocket.accept();
                
                // 得到socket的输入输出流
                ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                // 读取客户端发送的用户信息
                User user = (User) objectInputStream.readObject();
                
                // 创建一个Message对象，准备回复客户端验证信息
                Message message = new Message();
                
                // 验证得到的用户信息
                switch (checkUser(user)) {
                    // 验证成功，将验证信息发送给客户端
                    case MessageType.MESSAGE_LOGIN_SUCCESS:
                        System.out.println(formatter.format(LocalDateTime.now())
                                + " | 用户[" + user.getUserID() + "]已登录");
                        // 验证成功，将验证信息发送给客户端
                        message.setMessageType(MessageType.MESSAGE_LOGIN_SUCCESS);
                        objectOutputStream.writeObject(message);
    
                        // 创建一个线程与客户端保持通信
                        ServerConnectClientThread serverConnectClientThread =
                                new ServerConnectClientThread(socket, user.getUserID());
                        serverConnectClientThread.start();
    
                        // 将线程加入到管理集合中
                        ManageConnectionThreads.addServerConnectClientThread(
                                user.getUserID(), serverConnectClientThread);
                        
                        // 将服务器保存的离线消息发送给用户
                        if (offLineMessages.get(user.getUserID()) != null) {
                            ObjectOutputStream objectOutputStream1 =
                                    new ObjectOutputStream(serverConnectClientThread.getSocket().getOutputStream());
                            for (Message offLineMessage : offLineMessages.get(user.getUserID())) {
                                objectOutputStream1.writeObject(offLineMessage);
                            }
                            WeChatServer.offLineMessages.remove(user.getUserID());
                        }
                        break;
                    // 已经在线，将验证信息发送给客户端，并关闭socket
                    case MessageType.MESSAGE_USER_ONLINE:
                        System.out.println("用户[" + user.getUserID() + "]已经在线");
                        message.setMessageType(MessageType.MESSAGE_USER_ONLINE);
                        objectOutputStream.writeObject(message);
                        socket.close();
                        break;
                    // 登录失败，将验证信息发送给客户端，并关闭socket
                    case MessageType.MESSAGE_LOGIN_FAIL:
                        System.out.println(formatter.format(LocalDateTime.now())
                                + "用户[" + user.getUserID() + "] 密码[" + user.getPassword() + "] 登陆失败");
                        message.setMessageType(MessageType.MESSAGE_LOGIN_FAIL);
                        objectOutputStream.writeObject(message);
                        socket.close();
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 验证用户信息是否存在
     *
     * @param user 用户对象
     * @return 用户信息是否存在
     */
    private String checkUser(User user) {
        User user1 = validUsers.get(user.getUserID());
        // 用户不存在
        if (user1 == null) {
            return MessageType.MESSAGE_USER_NO_EXIST;
        }
        // 登陆失败
        if (!(user1.getPassword().equals(user.getPassword()))) {
            return MessageType.MESSAGE_LOGIN_FAIL;
        }
        // 用户已在线
        if (ManageConnectionThreads.getServerConnectClientThread(user.getUserID()) != null) {
            return MessageType.MESSAGE_USER_ONLINE;
        }
        return MessageType.MESSAGE_LOGIN_SUCCESS;
    }
}
