/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sftptransfer;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.File;
import java.io.FileFilter;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Calendar;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 *
 * @author D.Alekseenko
 */
public class SFTPTransfer extends Task
{

    private String host = "";
    private static String USERNAME = "";
    private static String PASSWORD = "!";
    private static String REMOTE_DIR = "/home/builder/rpmbuild/";
    private static String LOCAL_FILE = "";

    private FileFilter fileFilter = new FileFilter()
    {
        @Override
        public boolean accept(File pathname)
        {
            if (pathname.getName().contains(".jar"))
            {
                return true;
            }
            else if (pathname.getName().contains(".spec"))
            {
                return true;
            }
            else if (pathname.getName().contains(".service"))
            {
                return true;
            }
            else if (pathname.getName().contains(".sql"))
            {
                return true;
            }
            return false;
        }
    };

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
        try
        {
            SFTPTransfer sftp = new SFTPTransfer();
            sftp.execute();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    @Override
    public void execute() throws BuildException
    {
        try
        {
            host = System.getProperty("sftp.server.name", "");
            if (host.isEmpty())
            {
                printErr("Не указан sftp сервер");
                System.exit(1);
            }

            InetAddress[] addreses = InetAddress.getAllByName(host);

            printOut("Адрес удалённого сервера: " + host);
            printOut("Отправка, сборка и получение RPM файла с удалённого сервера");
            if (sendFiles() && buildRPM())
            {
                getRPM();
            }
        }
        catch (Exception ex)
        {
            printErr("Незвестный host сервер: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public boolean sendFiles()
    {
        try
        {
            printOut("Отправка данных на удалённый сервер по SFTP");
            JSch jSch = new JSch();
            Session session = jSch.getSession(USERNAME, host, 22);
            session.setPassword(PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            ChannelSftp channelSFTP = (ChannelSftp) session.openChannel("sftp");
            channelSFTP.connect();

            printOut("Получаем список файлов из директории: C:\\work_oe\\build\\");
            File file = new File(LOCAL_FILE);
            for (File listFile : file.listFiles(fileFilter))
            {
                if (listFile.getName().contains(".spec"))
                {
                    channelSFTP.put(listFile.getAbsolutePath(), REMOTE_DIR + "/SPECS");
                }
                else
                {
                    channelSFTP.put(listFile.getAbsolutePath(), REMOTE_DIR + "/SOURCES");
                }
            }
            String path = LOCAL_FILE + "\\fuelext";
            printOut("Получаем список файлов из директории: " + path);
            file = new File(path);
            for (File listFile : file.listFiles(fileFilter))
            {
                channelSFTP.put(listFile.getAbsolutePath(), REMOTE_DIR + "/SOURCES");
            }

            path = LOCAL_FILE + "\\fuelext\\ehotrdb";
            printOut("Получаем список файлов из директории: " + path);
            file = new File(path);
            for (File listFile : file.listFiles(fileFilter))
            {
                channelSFTP.put(listFile.getAbsolutePath(), REMOTE_DIR + "/SOURCES");
            }

            path = LOCAL_FILE + "\\fuelext\\ehotrdb\\upgrade";
            printOut("Получаем список файлов из директории: " + path);
            file = new File(path);
            for (File listFile : file.listFiles(fileFilter))
            {
                channelSFTP.put(listFile.getAbsolutePath(), REMOTE_DIR + "/SOURCES/upgrade");
            }

            channelSFTP.disconnect();
            session.disconnect();
            printOut("Файлы переданы на удалённый сервер успешно");
            return true;
        }
        catch (Exception ex)
        {
            printErr("Ошибка отправки файлов на удалённый сервер: " + ex.getMessage());
            ex.printStackTrace();
        }
        return false;
    }

    public boolean buildRPM()
    {
        try
        {
            printOut("Формируем RPM файл для OS Linux");
            JSch jSch = new JSch();
            Session session = jSch.getSession(USERNAME, host, 22);
            session.setPassword(PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            StringBuilder sb = new StringBuilder();
            sb.append("#!/bin/bash").append("\n");
            sb.append("rpmbuild -bb /home/builder/rpmbuild/SPECS/setupehotr.spec").append("\n");
            channelExec.setCommand(sb.toString());
            channelExec.connect();

            // Пишем вывод сборки RPM в консоль
            InputStream in = channelExec.getInputStream();
            byte[] tmp = new byte[1024];
            while (true)
            {
                while (in.available() > 0)
                {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0)
                    {
                        break;
                    }
                    printOut(new String(tmp, 0, i));
                }
                if (channelExec.isClosed())
                {
                    if (in.available() > 0)
                    {
                        continue;
                    }
                    printOut("exit-status: " + channelExec.getExitStatus());
                    break;
                }
                try
                {
                    Thread.sleep(1000);
                }
                catch (Exception ee)
                {
                }
            }
            channelExec.disconnect();
            session.disconnect();
            return true;
        }
        catch (Exception ex)
        {
            printErr("Неудалось выполнить скрипт на удалённом сервере: " + ex.getMessage());
            ex.printStackTrace();
        }
        return false;
    }

    public boolean getRPM()
    {
        try
        {
            printOut("Забираем сформированный RPM файл");
            JSch jSch = new JSch();
            Session session = jSch.getSession(USERNAME, host, 22);
            session.setPassword(PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            ChannelSftp channelSFTP = (ChannelSftp) session.openChannel("sftp");
            channelSFTP.connect();

            channelSFTP.get("/home/builder/rpmbuild/RPMS/*.rpm", "C:\\work_oe\\build");
            channelSFTP.disconnect();
            session.disconnect();
            return true;
        }
        catch (Exception ex)
        {
            printErr("Ошибка получения файла RPM с удалённого сервра: " + ex.getMessage());
            ex.printStackTrace();
        }
        return false;
    }

    private void printOut(String message)
    {
        System.out.println(String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS", Calendar.getInstance().getTime()) + " [" + Thread.currentThread().getId() + "] " + message);
    }

    private void printErr(String message)
    {
        System.err.println(String.format("%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS", Calendar.getInstance().getTime()) + " [" + Thread.currentThread().getId() + "] " + message);
    }

}
