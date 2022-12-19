package GameObject;

import java.awt.*;

public class Enemy {
    public Image enemyImage;
    Attribute attribute;
    public int attackCountDown;
    int attackPower;
    int defencePower;
    int healthValue;
    boolean isSurvive = true;
    boolean haveSkill;
    Skill skill;

    public int x;
    public int y;


    public Enemy(Image enemyImage, Attribute attribute,
                 int attackCountDown, int attackPower, int defencePower,
                 int healthValue, int x, int y, Skill skill) {
        this.enemyImage = enemyImage;
        this.attribute = attribute;
        this.attackCountDown = attackCountDown;
        this.attackPower = attackPower;
        this.defencePower = defencePower;
        this.healthValue = healthValue;
        this.skill = skill;
        this.x = x;
        this.y = y;

        this.haveSkill = true;
    }

    public Enemy(Image enemyImage, Attribute attribute,
                 int attackCountDown, int attackPower, int defencePower,
                 int healthValue, int x, int y) {
        this.enemyImage = enemyImage;
        this.attribute = attribute;
        this.attackCountDown = attackCountDown;
        this.attackPower = attackPower;
        this.defencePower = defencePower;
        this.healthValue = healthValue;
        this.x = x;
        this.y = y;

        this.haveSkill = false;
    }

    public boolean beAttacked(int damage) {
        int finalDamage = (damage >= defencePower) ? (damage - defencePower) : 1;
        healthValue -= finalDamage;
        isSurvive = healthValue > 0;
        return isSurvive;
    }

}
