package tkaxv7s.xposed.sesame.model.task.antBookRead;

import org.json.JSONArray;
import org.json.JSONObject;

import tkaxv7s.xposed.sesame.data.ModelFields;
import tkaxv7s.xposed.sesame.data.RuntimeInfo;
import tkaxv7s.xposed.sesame.data.modelFieldExt.BooleanModelField;
import tkaxv7s.xposed.sesame.data.ModelTask;
import tkaxv7s.xposed.sesame.model.base.TaskCommon;
import tkaxv7s.xposed.sesame.util.Log;
import tkaxv7s.xposed.sesame.util.RandomUtil;
import tkaxv7s.xposed.sesame.util.StringUtil;

public class AntBookRead extends ModelTask {
    private static final String TAG = AntBookRead.class.getSimpleName();

    @Override
    public String getName() {
        return "读书听书";
    }

    private BooleanModelField antBookRead;

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(antBookRead = new BooleanModelField("antBookRead", "开启读书听书", false));
        return modelFields;
    }

    @Override
    public Boolean check() {
        if (!antBookRead.getValue()) {
            return false;
        }
        if (TaskCommon.IS_ENERGY_TIME || !TaskCommon.IS_AFTER_8AM) {
            return false;
        }
        long executeTime = RuntimeInfo.getInstance().getLong("consumeGold", 0);
        return System.currentTimeMillis() - executeTime >= 21600000;
    }

    @Override
    public void run() {
        try {
            RuntimeInfo.getInstance().put("consumeGold", System.currentTimeMillis());
            queryTaskCenterPage();
            queryTask();
            queryTreasureBox();
        } catch (Throwable t) {
            Log.i(TAG, "start.run err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void queryTaskCenterPage() {
        try {
            String s = AntBookReadRpcCall.queryTaskCenterPage();
            JSONObject jo = new JSONObject(s);
            if (jo.getBoolean("success")) {
                JSONObject data = jo.getJSONObject("data");
                String todayPlayDurationText = data.getJSONObject("benefitAggBlock").getString("todayPlayDurationText");
                int PlayDuration = Integer.parseInt(StringUtil.getSubString(todayPlayDurationText, "今日听读时长", "分钟"));
                if (PlayDuration < 450) {
                    jo = new JSONObject(AntBookReadRpcCall.queryHomePage());
                    if (jo.getBoolean("success")) {
                        JSONArray bookList = jo.getJSONObject("data").getJSONArray("dynamicCardList").getJSONObject(0)
                                .getJSONObject("data").getJSONArray("bookList");
                        int bookListLength = bookList.length();
                        int postion = RandomUtil.nextInt(0, bookListLength - 1);
                        JSONObject book = bookList.getJSONObject(postion);
                        String bookId = book.getString("bookId");
                        jo = new JSONObject(AntBookReadRpcCall.queryReaderContent(bookId));
                        if (jo.getBoolean("success")) {
                            String nextChapterId = jo.getJSONObject("data").getString("nextChapterId");
                            String name = jo.getJSONObject("data").getJSONObject("readerHomePageVO").getString("name");
                            for (int i = 0; i < 17; i++) {
                                int energy = 0;
                                jo = new JSONObject(AntBookReadRpcCall.syncUserReadInfo(bookId, nextChapterId));
                                if (jo.getBoolean("success")) {
                                    jo = new JSONObject(AntBookReadRpcCall.queryReaderForestEnergyInfo(bookId));
                                    if (jo.getBoolean("success")) {
                                        String tips = jo.getJSONObject("data").getString("tips");
                                        if (tips.contains("已得")) {
                                            energy = Integer.parseInt(StringUtil.getSubString(tips, "已得", "g"));
                                        }
                                        Log.forest("阅读书籍📚[" + name + "]#累计能量" + energy + "g");
                                    }
                                }
                                if (energy >= 150) {
                                    break;
                                } else {
                                    Thread.sleep(1500L);
                                }
                            }
                        }
                    }
                }
            } else {
                Log.record(jo.getString("resultDesc"));
                Log.i(s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryTaskCenterPage err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void queryTask() {
        boolean doubleCheck = false;
        try {
            String s = AntBookReadRpcCall.queryTaskCenterPage();
            JSONObject jo = new JSONObject(s);
            if (jo.getBoolean("success")) {
                JSONObject data = jo.getJSONObject("data");
                JSONArray userTaskGroupList = data.getJSONObject("userTaskListModuleVO")
                        .getJSONArray("userTaskGroupList");
                for (int i = 0; i < userTaskGroupList.length(); i++) {
                    jo = userTaskGroupList.getJSONObject(i);
                    JSONArray userTaskList = jo.getJSONArray("userTaskList");
                    for (int j = 0; j < userTaskList.length(); j++) {
                        JSONObject taskInfo = userTaskList.getJSONObject(j);
                        String taskStatus = taskInfo.getString("taskStatus");
                        String taskType = taskInfo.getString("taskType");
                        String title = taskInfo.getString("title");
                        if ("TO_RECEIVE".equals(taskStatus)) {
                            if ("READ_MULTISTAGE".equals(taskType)) {
                                JSONArray multiSubTaskList = taskInfo.getJSONArray("multiSubTaskList");
                                for (int k = 0; k < multiSubTaskList.length(); k++) {
                                    taskInfo = multiSubTaskList.getJSONObject(k);
                                    taskStatus = taskInfo.getString("taskStatus");
                                    if ("TO_RECEIVE".equals(taskStatus)) {
                                        String taskId = taskInfo.getString("taskId");
                                        collectTaskPrize(taskId, taskType, title);
                                    }
                                }
                            } else {
                                String taskId = taskInfo.getString("taskId");
                                collectTaskPrize(taskId, taskType, title);
                            }
                        } else if ("NOT_DONE".equals(taskStatus)) {
                            if ("AD_VIDEO_TASK".equals(taskType)) {
                                String taskId = taskInfo.getString("taskId");
                                for (int m = 0; m < 5; m++) {
                                    taskFinish(taskId, taskType);
                                    Thread.sleep(1500L);
                                    collectTaskPrize(taskId, taskType, title);
                                    Thread.sleep(1500L);
                                }
                            } else if ("FOLLOW_UP".equals(taskType) || "JUMP".equals(taskType)) {
                                String taskId = taskInfo.getString("taskId");
                                taskFinish(taskId, taskType);
                                doubleCheck = true;
                            }
                        }
                    }
                }
                if (doubleCheck)
                    queryTask();
            } else {
                Log.record(jo.getString("resultDesc"));
                Log.i(s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryTask err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void collectTaskPrize(String taskId, String taskType, String name) {
        try {
            String s = AntBookReadRpcCall.collectTaskPrize(taskId, taskType);
            JSONObject jo = new JSONObject(s);
            if (jo.getBoolean("success")) {
                int coinNum = jo.getJSONObject("data").getInt("coinNum");
                Log.other("阅读任务📖[" + name + "]#" + coinNum);
            }
        } catch (Throwable t) {
            Log.i(TAG, "collectTaskPrize err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void taskFinish(String taskId, String taskType) {
        try {
            String s = AntBookReadRpcCall.taskFinish(taskId, taskType);
            JSONObject jo = new JSONObject(s);
            if (jo.getBoolean("success")) {

            }
        } catch (Throwable t) {
            Log.i(TAG, "taskFinish err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void queryTreasureBox() {
        try {
            String s = AntBookReadRpcCall.queryTreasureBox();
            JSONObject jo = new JSONObject(s);
            if (jo.getBoolean("success")) {
                JSONObject treasureBoxVo = jo.getJSONObject("data").getJSONObject("treasureBoxVo");
                if (treasureBoxVo.has("countdown"))
                    return;
                String status = treasureBoxVo.getString("status");
                if ("CAN_OPEN".equals(status)) {
                    jo = new JSONObject(AntBookReadRpcCall.openTreasureBox());
                    if (jo.getBoolean("success")) {
                        int coinNum = jo.getJSONObject("data").getInt("coinNum");
                        Log.other("阅读任务📖[打开宝箱]#" + coinNum);
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryTreasureBox err:");
            Log.printStackTrace(TAG, t);
        }
    }
}
