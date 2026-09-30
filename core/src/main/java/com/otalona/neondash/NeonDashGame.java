package com.otalona.neondash;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.Iterator;
import java.util.Random;

/** A small, asset-free 3D lane runner made with libGDX. */
public class NeonDashGame extends ApplicationAdapter {
    private static final Color BG = new Color(0.025f, 0.04f, 0.09f, 1f);
    private static final float[] LANES = {-2.1f, 0f, 2.1f};
    private final Random random = new Random();
    private final Array<Obstacle> obstacles = new Array<>();
    private final Array<ModelInstance> laneMarks = new Array<>();

    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private Environment environment;
    private SpriteBatch spriteBatch;
    private BitmapFont font;
    private Model playerModel, floorModel, laneMarkModel, railModel, obstacleModel, accentModel;
    private ModelInstance player, floorLeft, leftRail, rightRail;
    private float playerX, playerY, jumpTime, spawnTimer, elapsed, speed = 12f;
    private int lane = 1, score, best;
    private boolean started, gameOver;
    private int touchStartX, touchStartY;

    @Override public void create() {
        modelBatch = new ModelBatch();
        spriteBatch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.25f);
        camera = new PerspectiveCamera(67f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0f, 7.2f, 10.5f);
        camera.lookAt(0f, 0.5f, -8f);
        camera.near = 0.1f;
        camera.far = 90f;
        camera.update();

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.72f, 0.77f, 0.92f, 1f));
        environment.add(new DirectionalLight().set(0.85f, 0.9f, 1f, -0.5f, -1f, -0.25f));
        ModelBuilder builder = new ModelBuilder();
        long attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        floorModel = builder.createBox(9.2f, 0.35f, 72f, material(new Color(0.055f,0.075f,0.14f,1)), attrs);
        laneMarkModel = builder.createBox(0.075f, 0.035f, 1.2f, material(new Color(0.16f,0.25f,0.39f,1)), attrs);
        railModel = builder.createBox(0.12f, 0.16f, 72f, material(new Color(0.10f,0.85f,0.78f,1)), attrs);
        playerModel = builder.createBox(0.95f, 1.0f, 0.82f, material(new Color(0.12f,0.95f,0.8f,1)), attrs);
        obstacleModel = builder.createBox(1.1f, 1.5f, 0.85f, material(new Color(1f,0.25f,0.47f,1)), attrs);
        accentModel = builder.createBox(1.18f, 0.14f, 0.92f, material(new Color(1f,0.73f,0.22f,1)), attrs);
        floorLeft = new ModelInstance(floorModel); floorLeft.transform.setToTranslation(0f,-0.28f,-17f);
        leftRail = new ModelInstance(railModel); leftRail.transform.setToTranslation(-4.45f,-0.02f,-17f);
        rightRail = new ModelInstance(railModel); rightRail.transform.setToTranslation(4.45f,-0.02f,-17f);
        player = new ModelInstance(playerModel);
        for (int i=0; i<32; i++) {
            for (float x : new float[]{-1.05f, 1.05f}) {
                ModelInstance mark = new ModelInstance(laneMarkModel);
                mark.transform.setToTranslation(x,-0.08f, 9f-i*2.25f);
                laneMarks.add(mark);
            }
        }
        best = Gdx.app.getPreferences("neon-dash").getInteger("best", 0);
        installInput();
    }

    private Material material(Color color) { return new Material(ColorAttribute.createDiffuse(color)); }

    private void installInput() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int keycode) {
                if (gameOver && (keycode == Input.Keys.SPACE || keycode == Input.Keys.ENTER)) { restart(); return true; }
                if (!started) started = true;
                if (keycode == Input.Keys.LEFT || keycode == Input.Keys.A) moveLane(-1);
                if (keycode == Input.Keys.RIGHT || keycode == Input.Keys.D) moveLane(1);
                if (keycode == Input.Keys.UP || keycode == Input.Keys.SPACE || keycode == Input.Keys.W) jump();
                return true;
            }
            @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                touchStartX = screenX; touchStartY = screenY; return true;
            }
            @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                if (!started || gameOver) { restart(); started = true; return true; }
                int dx = screenX-touchStartX, dy = screenY-touchStartY;
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 32) moveLane(dx < 0 ? -1 : 1);
                else if (dy < -24 || (Math.abs(dx) < 28 && Math.abs(dy) < 28)) jump();
                return true;
            }
        });
    }

    private void moveLane(int direction) { lane = MathUtils.clamp(lane + direction, 0, 2); }
    private void jump() { if (jumpTime <= 0.01f) jumpTime = 0.72f; }
    private void restart() {
        obstacles.clear(); lane = 1; playerX = 0; playerY = 0; jumpTime = 0;
        spawnTimer = 0.65f; elapsed = 0; score = 0; speed = 12f; gameOver = false;
    }

    @Override public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
        if (started && !gameOver) update(delta);
        ScreenUtils.clear(BG, true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        camera.viewportWidth = Gdx.graphics.getWidth(); camera.viewportHeight = Gdx.graphics.getHeight(); camera.update();
        playerX = MathUtils.lerp(playerX, LANES[lane], Math.min(1f, delta*12f));
        if (jumpTime > 0) { jumpTime = Math.max(0, jumpTime-delta); playerY = (float)Math.sin((0.72f-jumpTime)/0.72f*Math.PI)*1.55f; }
        else playerY = 0;
        player.transform.setToTranslation(playerX, 0.52f+playerY, 1.1f);
        for (ModelInstance mark : laneMarks) {
            float z = mark.transform.getTranslation(new Vector3()).z + (started && !gameOver ? speed*delta : 0);
            if (z > 12f) z -= 72f;
            mark.transform.setToTranslation(0f,-0.08f,z);
        }
        modelBatch.begin(camera);
        modelBatch.render(floorLeft, environment); modelBatch.render(leftRail, environment); modelBatch.render(rightRail, environment);
        for (ModelInstance mark : laneMarks) modelBatch.render(mark, environment);
        modelBatch.render(player, environment);
        for (Obstacle obstacle : obstacles) { modelBatch.render(obstacle.body, environment); modelBatch.render(obstacle.accent, environment); }
        modelBatch.end();
        drawHud();
    }

    private void update(float delta) {
        elapsed += delta; speed = Math.min(21f, 12f + elapsed*0.20f); score = (int)(elapsed*10f);
        spawnTimer -= delta;
        if (spawnTimer <= 0) {
            int chosenLane = random.nextInt(3);
            float z = -32f;
            ModelInstance body = new ModelInstance(obstacleModel);
            body.transform.setToTranslation(LANES[chosenLane], 0.75f, z);
            ModelInstance accent = new ModelInstance(accentModel);
            accent.transform.setToTranslation(LANES[chosenLane], 1.42f, z);
            obstacles.add(new Obstacle(chosenLane, body, accent));
            spawnTimer = Math.max(0.72f, 1.32f-elapsed*0.006f) + random.nextFloat()*0.42f;
        }
        Iterator<Obstacle> iterator = obstacles.iterator();
        while (iterator.hasNext()) {
            Obstacle obstacle = iterator.next();
            Vector3 p = obstacle.body.transform.getTranslation(new Vector3());
            p.z += speed*delta;
            obstacle.body.transform.setToTranslation(LANES[obstacle.lane], 0.75f, p.z);
            obstacle.accent.transform.setToTranslation(LANES[obstacle.lane], 1.42f, p.z);
            if (p.z > 2.2f) iterator.remove();
            else if (p.z > 0.45f && p.z < 1.85f && obstacle.lane == lane && playerY < 0.6f) endGame();
        }
    }

    private void endGame() {
        gameOver = true;
        if (score > best) { best = score; Gdx.app.getPreferences("neon-dash").putInteger("best", best).flush(); }
    }

    private void drawHud() {
        spriteBatch.begin();
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, "NEON DASH 3D", 24, Gdx.graphics.getHeight()-30);
        font.draw(spriteBatch, "PONTOS  " + score, 24, Gdx.graphics.getHeight()-62);
        font.draw(spriteBatch, "RECORDE  " + best, 24, Gdx.graphics.getHeight()-88);
        String message;
        if (gameOver) message = "FIM DE JOGO\nToque para tentar de novo";
        else if (!started) message = "DESVIE DOS BLOCOS\nDeslize para os lados • deslize para cima para pular\nToque para começar";
        else message = "← → trocar de faixa     ↑ pular";
        font.setColor(new Color(0.78f,0.9f,1f,1f));
        font.draw(spriteBatch, message, 24, 142);
        spriteBatch.end();
    }

    @Override public void resize(int width, int height) { if (camera != null) { camera.viewportWidth = width; camera.viewportHeight = height; camera.update(); } }
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void dispose() {
        if (modelBatch != null) modelBatch.dispose();
        if (spriteBatch != null) spriteBatch.dispose();
        if (font != null) font.dispose();
        if (floorModel != null) floorModel.dispose();
        if (laneMarkModel != null) laneMarkModel.dispose();
        if (railModel != null) railModel.dispose();
        if (playerModel != null) playerModel.dispose();
        if (obstacleModel != null) obstacleModel.dispose();
        if (accentModel != null) accentModel.dispose();
    }
    private static final class Obstacle {
        final int lane; final ModelInstance body, accent;
        Obstacle(int lane, ModelInstance body, ModelInstance accent) { this.lane=lane; this.body=body; this.accent=accent; }
    }
}
