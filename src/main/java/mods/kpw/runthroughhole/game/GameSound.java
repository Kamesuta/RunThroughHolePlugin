package mods.kpw.runthroughhole.game;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * ゲーム内で使用する効果音を一元管理するenum
 */
public enum GameSound {
    /** キューブ回転成功時 */
    ROTATION(Sound.ENTITY_SNIFFER_DROP_SEED, 1.0f, 1.0f),

    /** 移動成功時 */
    MOVE(Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f),

    /** 加速開始時 */
    BOOST_START(Sound.ENTITY_GHAST_SHOOT, 1.0f, 1.0f),

    /** 連続加速開始時（プレビュー緑でSpace押下） */
    CONTINUOUS_BOOST_START(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f),

    /** 穴をなぞった時 */
    HOLE_TRACE(Sound.BLOCK_GLASS_BREAK, 0.3f, 1.8f),

    /** 穴を完全になぞり終えた時 */
    HOLE_COMPLETE(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f),

    /** ゲームオーバー時の爆発音 */
    GAME_OVER_EXPLOSION(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

    private final Sound sound;
    private final float volume;
    private final float pitch;

    /**
     * コンストラクタ
     *
     * @param sound  再生するSound
     * @param volume 音量（0.0-1.0）
     * @param pitch  ピッチ（0.5-2.0）
     */
    GameSound(Sound sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }

    /**
     * プレイヤーの位置で効果音を再生
     *
     * @param player プレイヤー
     */
    public void play(Player player) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
