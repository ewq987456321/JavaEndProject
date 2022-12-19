package Scenes;

import GameKernel.utils.controllers.AudioResourceController;
import GameKernel.utils.controllers.ImageController;
import GameKernel.utils.core.CommandSolver;
import GameKernel.utils.core.Scene;
import GameObject.Attribute;
import GameObject.Ball;
import GameObject.Enemy;

import java.awt.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static GameObject.GlobalParameter.*;

public class GameScene extends Scene {
    ArrayList<ArrayList<Ball>> balls;
    ArrayList<ArrayList<Enemy>> enemies;
    Ball ControlBall;
    Random random;
    int turnTime = 60; // 轉珠時長
    int turnTimeCountDown = -1; // 倒數
    Timer turnBallTimer; // 計時物件
    ScheduledExecutorService eliminateDelay;
    ScheduledExecutorService skyFallDelay;
    ScheduledExecutorService two;
    boolean canTurning = true; // 轉珠開關
    int numLevel = 3; // 關卡數量
    int nowLevel = 0; // 目前在第幾關
    int[] eliminateBalls = new int[1];

    /**
     * 畫面開始時執行
     * 初始化轉珠盤、敵人、我方角色
     */
    @Override
    public void sceneBegin() {
        random = new Random();
        balls = new ArrayList<>();
        enemies = new ArrayList<>();

        // init Balls
        for (int i = 0; i < BALLPLATE_HEIGHT; i++) {
            balls.add(new ArrayList<>());
            for (int j = 0; j < BALLPLATE_WIDTH; j++) {
                balls.get(i).add(new Ball(j * BALL_WIDTH + SCREEN_WIDTH / 4, i * BALL_HEIGHT + (int) (SCREEN_WIDTH * 0.3),
                        j, i, Attribute.values()[random.nextInt(6)]));
            }
        }

        // init enemies
        for (int i = 0; i < numLevel; i++) {
            enemies.add(new ArrayList<>());
        }
//            for (int j = 0; j < 3; j++) {
        enemies.get(0).add(new Enemy(ImageController.instance().tryGetImage("../../../boss/巨象.png"),
                Attribute.None, 1, 1000, 100000, 10,
                0 * BALL_WIDTH + SCREEN_WIDTH / 2 - (int) (BALL_WIDTH * 1.5), 30));
        enemies.get(0).add(new Enemy(ImageController.instance().tryGetImage("../../../boss/毒龍.png"),
                Attribute.None, 1, 1000, 100, 100000,
                1 * BALL_WIDTH + SCREEN_WIDTH / 2 - (int) (BALL_WIDTH * 1.5), 30));
        enemies.get(0).add(new Enemy(ImageController.instance().tryGetImage("../../../boss/毒龍.png"),
                Attribute.None, 1, 1000, 100, 100000,
                2 * BALL_WIDTH + SCREEN_WIDTH / 2 - (int) (BALL_WIDTH * 1.5), 30));

        AudioResourceController.getInstance().loop("../../../Audio/mainBGM.wav", Integer.MAX_VALUE);
    }

    @Override
    public void sceneEnd() {

    }

    @Override
    public void paint(Graphics g) {
        g.setColor(Color.white);
        g.setFont(new Font("標楷體", Font.PLAIN, 24));
        g.drawString(Integer.toString(turnTimeCountDown), 10, 50);
        // draw BallPlate
        for (ArrayList<Ball> list : balls) {
            for (Ball ball : list) {
                g.drawImage(ball.image, ball.x, ball.y,
                        BALL_WIDTH, BALL_HEIGHT, null);
            }
        }
        // draw enemies
        for (Enemy enemy : enemies.get(nowLevel)) {
            g.drawImage(enemy.enemyImage, enemy.x, enemy.y, 100, 100, null);
        }
    }

    @Override
    public void update() {
        // 消珠 ＆ 天降
        if (!canTurning /*轉珠結束*/) {
            if (two == null || two.isShutdown())
                eliminateBalls = EliminateBall();
            if ((two == null || two.isShutdown()) && eliminateBalls != null) {
                if (two == null) {
                    runSkyFallAndComplement(eliminateBalls);
                }
                eliminateBalls = null;
            }
        }
    }

    /**
     * 依序執行執行天將、補珠，不影響繪圖程序
     */
    private void runSkyFallAndComplement(int[] eliminateBallsO) {
        int[] eliminateBalls = new int[13];
        CountDownLatch cd1 = new CountDownLatch(1);
        CountDownLatch cd2 = new CountDownLatch(1);
        two = Executors.newSingleThreadScheduledExecutor();
        // 天降、天降後消珠
        Runnable task1 = () -> {
            skyFallDelay = Executors.newSingleThreadScheduledExecutor();
            skyFallDelay.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    boolean fallen = false;
                    for (int i = 0; i < BALLPLATE_WIDTH; i++) {
                        boolean findNone = false;
                        for (int j = BALLPLATE_HEIGHT - 1; j >= 0; j--) {
                            Ball tmp = balls.get(j).get(i);
                            if (tmp.attribute == Attribute.None) findNone = true;
                            if (tmp.attribute != Attribute.None && findNone) {
                                for (int k = j; k >= 0; k--) {
                                    fallen = true;
                                    tmp = balls.get(k).get(i);
                                    exchangeBall(tmp, balls.get(k + 1).get(i));
                                }
                                break;
                            }
                        }
                    }
                    if (!fallen) {
                        if (!EliminateBall(eliminateBalls)) {
                            System.out.println("天降結束");
                            cd1.countDown();
                            skyFallDelay.shutdown();
                        }
                    }
                }
            }, 0, 500, TimeUnit.MILLISECONDS);
        };
        // 補珠、轉珠延遲
        Runnable task2 = () -> {
            try {
                cd1.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println("補珠開始");
            eliminateDelay = Executors.newSingleThreadScheduledExecutor();
            eliminateDelay.scheduleWithFixedDelay(new TimerTask() {
                int countDown = 4;

                @Override
                public void run() {
                    if (countDown == 4) {
                        ReplenishBall();
                        // 補珠-可以轉珠delay
                    } else if (countDown == 1) {
                        eliminateDelay.shutdown();
                        cd2.countDown();
                        // TODO should move

                    }
                    countDown -= 1;
                }
            }, 0, 500, TimeUnit.MILLISECONDS);
        };
        // 攻擊、換關
        Runnable task3 = () -> {
            try {
                cd2.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            for (int i = 0; i < eliminateBalls.length; i++) {
                eliminateBallsO[i] += eliminateBalls[i];
            }
            // TODO calculate attack value

            if (eliminateBallsO[12] != -1) {
                for (int i = 0; i < 6; i++)
                    System.out.println("\u001B[34m" + Arrays.asList(Attribute.values()).get(i) + ": " + eliminateBallsO[i] + "\u001B[39m");
            }
            System.out.println("\u001B[34m" +eliminateBallsO[12] + "\u001B[39m");

            for (Enemy e : enemies.get(nowLevel)) {
                if (!e.beAttacked(100100)) {
                    enemies.get(nowLevel).remove(e);
                    break;
                }
            }
            if (enemies.get(nowLevel).size() == 0) nowLevel += 1;
            two = null;
            canTurning = true;
        };
        two.schedule(task1, 0, TimeUnit.SECONDS);
        two.schedule(task2, 0, TimeUnit.SECONDS);
        two.schedule(task3, 0, TimeUnit.SECONDS);
    }


    @Override
    public CommandSolver.MouseCommandListener mouseListener() {
        return (e, state, trigTime) -> {
            if (state == CommandSolver.MouseState.DRAGGED && canTurning) {
                Ball HitBall = checkMouseOnBall(e.getPoint());
                // get control ball
                if (HitBall != null) {
                    // 轉珠時間計時
                    if (turnTimeCountDown == -1) {
                        turnBallTimer = new Timer();
                        turnTimeCountDown = turnTime;
                    }
                    if (turnTimeCountDown == turnTime) {
                        turnBallTimer.scheduleAtFixedRate(new TimerTask() {
                            @Override
                            public void run() {
                                turnTimeCountDown -= 1;
                                if (turnTimeCountDown == -1) {
                                    canTurning = false;
                                    turnBallTimer.cancel();
                                }
                            }
                        }, 0, 1000);
                    }
                    // 移動的珠
                    if (ControlBall == null) {
                        ControlBall = HitBall;
                        // 被交換的珠
                    } else if (HitBall != ControlBall) {
                        // 交換兩珠子
                        exchangeBall(ControlBall, HitBall);
                        ControlBall = null;
                    }
                }
            }
            if (!canTurning || state == CommandSolver.MouseState.RELEASED) {
                // 回復成可以轉珠
                if (state == CommandSolver.MouseState.RELEASED) {
                    canTurning = false;
                    turnTimeCountDown = -1;
                    turnBallTimer.cancel();
                }
                ControlBall = null;
            }
        };
    }

    @Override
    public CommandSolver.KeyListener keyListener() {
        return null;
    }

    /**
     * 交換兩顆珠子
     *
     * @param firstBall  第一顆要交換的珠子
     * @param secondBall 第二顆要交換的珠子
     */
    private void exchangeBall(Ball firstBall, Ball secondBall) {
        int[] tmpPosition = {secondBall.y, secondBall.x};
        int[] indexFirstBall = {firstBall.indexY, firstBall.indexX};
        int[] indexSecondBall = {secondBall.indexY, secondBall.indexX};
        secondBall.indexX = indexFirstBall[1];
        secondBall.indexY = indexFirstBall[0];
        secondBall.x = firstBall.x;
        secondBall.y = firstBall.y;
        firstBall.indexX = indexSecondBall[1];
        firstBall.indexY = indexSecondBall[0];
        firstBall.x = tmpPosition[1];
        firstBall.y = tmpPosition[0];

        balls.get(indexFirstBall[0]).set(indexFirstBall[1], secondBall);
        balls.get(indexSecondBall[0]).set(indexSecondBall[1], firstBall);
    }

    /**
     * 消除水平、垂直三顆以上相同屬性的珠子
     * 回傳每種屬性（包含強化珠）消除個數
     *
     * @return eliminateBall: 前六個為個屬一般珠 後六為個屬強化珠 最後為Combo數，若無為-1
     */
    private int[] EliminateBall() {
        int[] eliminateBalls = new int[13]; // first six normal ball, next six string ball, final Combo
        int countCombo = -1;
        // vertical
        for (int i = 0; i < balls.get(0).size(); i++) {
            ArrayList<Ball> sames = new ArrayList<>();
            for (int j = 0; j < balls.size() - 1; j++) {
                sames.add(balls.get(j).get(i));
                if (balls.get(j).get(i).attribute == balls.get(j + 1).get(i).attribute && sames.get(0).attribute != Attribute.None) {
                    if (sames.contains(balls.get(j + 1).get(i)))
                        sames.add(balls.get(j + 1).get(i));
                } else {
                    if (sames.size() >= 3) {
                        countCombo += 1;
                        for (Ball same : sames) {
                            // TODO count strong ball
                            eliminateBalls[same.attribute.ordinal()] += 1;
                            balls.get(same.indexY).set(same.indexX, new Ball(same.x, same.y, same.indexX, same.indexY, Attribute.None));
                        }
                    }
                    sames.clear();
                }
                if (j + 1 == balls.size() - 1) {
                    if (sames.size() >= 2) {
                        if (balls.get(j + 1).get(i).attribute == sames.get(0).attribute && sames.get(0).attribute != Attribute.None)
                            sames.add(balls.get(j + 1).get(i));
                        countCombo += 1;
                        for (Ball same : sames) {
                            eliminateBalls[same.attribute.ordinal()] += 1;
                            balls.get(same.indexY).set(same.indexX, new Ball(same.x, same.y, same.indexX, same.indexY, Attribute.None));
                        }
                    }
                }
            }
        }

        // Horizontal
        for (ArrayList<Ball> list : balls) {
            ArrayList<Ball> sames = new ArrayList<>();
            for (int i = 0; i < list.size() - 1; i++) {
                sames.add(list.get(i));
                if (list.get(i).attribute == list.get(i + 1).attribute && sames.get(0).attribute != Attribute.None) {
                    if (sames.contains(list.get(i + 1)))
                        sames.add(list.get(i + 1));
                } else {
                    // Not same
                    if (sames.size() >= 3) {
                        countCombo += 1;
                        for (Ball same : sames) {
                            eliminateBalls[same.attribute.ordinal()] += 1;
                            list.set(same.indexX, new Ball(same.x, same.y, same.indexX, same.indexY, Attribute.None));
                        }
                    }
                    sames.clear();
                }
                // Hit wall
                if (i + 1 == list.size() - 1) {
                    if (sames.size() >= 2) {
                        if (list.get(i + 1).attribute == sames.get(0).attribute && sames.get(0).attribute != Attribute.None)
                            sames.add(list.get(i + 1));
                        countCombo += 1;
                        for (Ball same : sames) {
                            eliminateBalls[same.attribute.ordinal()] += 1;
                            list.set(same.indexX, new Ball(same.x, same.y, same.indexX, same.indexY, Attribute.None));
                        }
                    }
                }
            }
        }

        eliminateBalls[12] = (countCombo == -1) ? -1 : countCombo + 1;
        return eliminateBalls;
    }

    /**
     * 消除水平、垂直三顆以上相同屬性的珠子(可一直累積)
     * @param eliminateBalls 紀錄陣列
     * @return 是否有消除
     */
    private boolean EliminateBall(int[] eliminateBalls) {
        int countCombo = -1;
        // vertical
        for (int i = 0; i < balls.get(0).size(); i++) {
            ArrayList<Ball> sames = new ArrayList<>();
            for (int j = 0; j < balls.size() - 1; j++) {
                sames.add(balls.get(j).get(i));
                if (balls.get(j).get(i).attribute == balls.get(j + 1).get(i).attribute && sames.get(0).attribute != Attribute.None) {
                    if (sames.contains(balls.get(j + 1).get(i)))
                        sames.add(balls.get(j + 1).get(i));
                } else {
                    if (sames.size() >= 3) {
                        countCombo += 1;
                        for (Ball same : sames) {
                            // TODO count strong ball
                            eliminateBalls[same.attribute.ordinal()] += 1;
                            balls.get(same.indexY).set(same.indexX, new Ball(same.x, same.y, same.indexX, same.indexY, Attribute.None));
                        }
                    }
                    sames.clear();
                }
                if (j + 1 == balls.size() - 1) {
                    if (sames.size() >= 2) {
                        if (balls.get(j + 1).get(i).attribute == sames.get(0).attribute && sames.get(0).attribute != Attribute.None)
                            sames.add(balls.get(j + 1).get(i));
                        countCombo += 1;
                        for (Ball same : sames) {
                            eliminateBalls[same.attribute.ordinal()] += 1;
                            balls.get(same.indexY).set(same.indexX, new Ball(same.x, same.y, same.indexX, same.indexY, Attribute.None));
                        }
                    }
                }
            }
        }

        // Horizontal
        for (ArrayList<Ball> list : balls) {
            ArrayList<Ball> sames = new ArrayList<>();
            for (int i = 0; i < list.size() - 1; i++) {
                sames.add(list.get(i));
                if (list.get(i).attribute == list.get(i + 1).attribute && sames.get(0).attribute != Attribute.None) {
                    if (sames.contains(list.get(i + 1)))
                        sames.add(list.get(i + 1));
                } else {
                    // Not same
                    if (sames.size() >= 3) {
                        countCombo += 1;
                        for (Ball same : sames) {
                            eliminateBalls[same.attribute.ordinal()] += 1;
                            list.set(same.indexX, new Ball(same.x, same.y, same.indexX, same.indexY, Attribute.None));
                        }
                    }
                    sames.clear();
                }
                // Hit wall
                if (i + 1 == list.size() - 1) {
                    if (sames.size() >= 2) {
                        if (list.get(i + 1).attribute == sames.get(0).attribute && sames.get(0).attribute != Attribute.None)
                            sames.add(list.get(i + 1));
                        countCombo += 1;
                        for (Ball same : sames) {
                            eliminateBalls[same.attribute.ordinal()] += 1;
                            list.set(same.indexX, new Ball(same.x, same.y, same.indexX, same.indexY, Attribute.None));
                        }
                    }
                }
            }
        }

        eliminateBalls[12] += (countCombo == -1) ? 0 : countCombo + 1;
        return countCombo != -1;
    }

    /**
     * 將Attribute為None的珠子填充隨機屬性的正常珠子
     */
    private void ReplenishBall() {
        for (int i = 0; i < BALLPLATE_WIDTH; i++) {
            for (int j = 0; j < BALLPLATE_HEIGHT; j++) {
                if (balls.get(j).get(i).attribute == Attribute.None) {
                    Ball tmp = balls.get(j).get(i);
                    balls.get(j).set(i, new Ball(tmp.x, tmp.y, tmp.indexX, tmp.indexY, Attribute.values()[random.nextInt(6)]));
                }
            }
        }
    }

    /**
     * 檢查滑鼠位置在哪顆珠子上
     *
     * @param e 滑鼠的座標Point
     * @return 回傳所在珠子，若無則回傳null
     */
    public Ball checkMouseOnBall(Point e) {
        int x = e.x;
        int y = e.y;
        for (ArrayList<Ball> list : balls) {
            for (Ball ball : list) {
                if (x >= ball.x && x < ball.x + BALL_WIDTH && y >= ball.y && y < ball.y + BALL_HEIGHT) return ball;
            }
        }
        return null;
    }
}
