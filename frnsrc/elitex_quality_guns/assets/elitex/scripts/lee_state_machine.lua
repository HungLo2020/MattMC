local track_line_top = {value = 0}
local static_track_top = {value = 0}
local blending_track_top = {value = 0}

-- Credit goes to Silver hamster the creator of gunpowder revolution gunpack
local function increment(obj)
    obj.value = obj.value + 1
    return obj.value - 1
end

local STATIC_TRACK_LINE = increment(track_line_top)
local BASE_TRACK = increment(static_track_top)
local BOLT_CAUGHT_TRACK = increment(static_track_top)
local SAFETY_TRACK = increment(static_track_top) -- 待实现
local ADS_TRACK = increment(blending_track_top)
local MAIN_TRACK = increment(static_track_top)

local GUN_KICK_TRACK_LINE = increment(track_line_top)

local BLENDING_TRACK_LINE = increment(track_line_top)
local MOVEMENT_TRACK = increment(blending_track_top)
local LOOP_TRACK = increment(blending_track_top)

local function runPutAwayAnimation(context)
    local put_away_time = context:getPutAwayTime()
    local track = context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK)
    -- 播放 put_away 动画，并且将其剩余时长设为 context 传入的 put_away_time
    context:runAnimation("put_away", track, false, PLAY_ONCE_HOLD, put_away_time * 0.75)
    context:setAnimationProgress(track, 1, true)
    context:adjustAnimationProgress(track, -put_away_time, false)
end

local function isNoAmmo(context)
    return (not context:hasBulletInBarrel()) and (context:getAmmoCount() <= 0)
end

local function runReloadAnimation(context)
    local track = context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK)
    if (isNoAmmo(context)) then
        context:runAnimation("reload_howell_empty", track, false, PLAY_ONCE_STOP, 0)
    else
        context:runAnimation("reload_howell", track, false, PLAY_ONCE_STOP, 0)
    end
end

local function runInspectAnimation(context)
    local track = context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK)
    if (isNoAmmo(context)) then
        context:runAnimation("inspect_empty", track, false, PLAY_ONCE_STOP, 0.2)
    else
        context:runAnimation("inspect", track, false, PLAY_ONCE_STOP, 0.2)
    end
end

local base_track_state = {}

function base_track_state.entry(this, context)
    context:runAnimation("static_idle", context:getTrack(STATIC_TRACK_LINE, BASE_TRACK), false, LOOP, 0.2)
end

function base_track_state.update(this, context)
    if (context:getAttachment("MUZZLE") ~= "hamster:gas_tube") then
        context:runAnimation("static_idle", context:getTrack(STATIC_TRACK_LINE, BASE_TRACK), false, LOOP, 0)
    else
        context:runAnimation("howell_idle", context:getTrack(STATIC_TRACK_LINE, BASE_TRACK), false, LOOP, 0)
    end
end

local bolt_caught_states = {
    normal = {},
    bolt_caught = {}
}

function bolt_caught_states.normal.update(this, context)
    if (not context:hasBulletInBarrel()) then
        context:trigger(this.INPUT_BOLT_CAUGHT)
    end
end

function bolt_caught_states.normal.entry(this, context)
    this.bolt_caught_states.normal.update(this, context)
end

function bolt_caught_states.normal.transition(this, context, input)
    if (input == this.INPUT_BOLT_CAUGHT) then
        return this.bolt_caught_states.bolt_caught
    end
end

function bolt_caught_states.bolt_caught.entry(this, context)
    context:runAnimation("static_bolt_caught", context:getTrack(STATIC_TRACK_LINE, BOLT_CAUGHT_TRACK), false, LOOP, 0)
end

function bolt_caught_states.bolt_caught.update(this, context)
    if (context:hasBulletInBarrel()) then
        context:trigger(this.INPUT_BOLT_NORMAL)
    end
end

function bolt_caught_states.bolt_caught.transition(this, context, input)
    if (input == this.INPUT_BOLT_NORMAL) then
        context:stopAnimation(context:getTrack(STATIC_TRACK_LINE, BOLT_CAUGHT_TRACK))
        return this.bolt_caught_states.normal
    end
end

local main_track_states = {
    start = {},
    idle = {},
    reload = {
        retreat = "reload_retreat",
        need_ammo = 0,
        loaded_ammo = 0,
        emptyload = 0
    },
    inspect = {
        retreat = "inspect_retreat"
    },
    shoot_mode = 0,
    final = {},
    bayonet_counter = 0
}

function main_track_states.start.transition(this, context, input)
    if (input == INPUT_DRAW) then
        context:runAnimation("draw", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0)
        return main_track_states.idle
    end
end

function main_track_states.idle.transition(this, context, input)
    if (input == INPUT_PUT_AWAY) then
        runPutAwayAnimation(context)
        return main_track_states.final
    end
    if (input == INPUT_RELOAD) then
        if (context:getAttachment("MUZZLE") ~= "hamster:gas_tube") then
            return main_track_states.reload
        else
            runReloadAnimation(context)
            return this.main_track_states.idle
        end
    end
    if (input == INPUT_BOLT and context:getAttachment("MUZZLE") ~= "hamster:gas_tube") then
        if (main_track_states.shoot_mode == 1) then
            context:runAnimation("rapid_bolt", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0.2)
        else
            if (context:getAttachment("SCOPE") ~= "tacz:empty") then
                context:runAnimation("bolt_scope", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0.2)
            else
                context:runAnimation("bolt", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0.2)
            end
            context:runAnimation("bolt_charge", context:getTrack(STATIC_TRACK_LINE, BOLT_CAUGHT_TRACK), false, PLAY_ONCE_STOP, 0.2)
        end
        return main_track_states.idle
    end
    if (input == INPUT_INSPECT) then
        runInspectAnimation(context)
        return main_track_states.inspect
    end
    if (input == INPUT_BAYONET_MUZZLE) then
        local counter = main_track_states.bayonet_counter
        local animationName = "melee_bayonet_" .. tostring(counter + 1)
        main_track_states.bayonet_counter = (counter + 1) % 3
        context:runAnimation(animationName, context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0.2)
        return main_track_states.idle
    end
    if (input == INPUT_BAYONET_STOCK) then
        context:runAnimation("melee_stock", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0.2)
        return main_track_states.idle
    end
    if (input == INPUT_BAYONET_PUSH) then
        context:runAnimation("melee_push", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0.2)
        return main_track_states.idle
    end
end

function main_track_states.reload.entry(this, context)
    if (isNoAmmo(context)) then
        main_track_states.reload.emptyload = 1
        context:runAnimation("reload_start_empty", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_HOLD, 0.1)
    elseif(context:getAmmoCount() == 0) then
        context:runAnimation("reload_start_1round", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_HOLD, 0.1)
    else
        context:runAnimation("reload_start", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_HOLD, 0.1)
    end
    main_track_states.reload.need_ammo = context:getMaxAmmoCount() - context:getAmmoCount()
    main_track_states.reload.loaded_ammo = 0
    if(context:hasBulletInBarrel())then
        main_track_states.reload.loaded_ammo = main_track_states.reload.loaded_ammo + 1
    end
end

function main_track_states.reload.update(this, context)
    if (main_track_states.reload.loaded_ammo > main_track_states.reload.need_ammo or not context:hasAmmoToConsume()) then
        context:trigger(main_track_states.reload.retreat)
    else
        local track = context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK)
        -- 等待 intro 结束，然后循环播放 loop 动画
        if(context:isHolding(track)) then
            if(main_track_states.reload.emptyload == 1) then
                if (main_track_states.reload.need_ammo - main_track_states.reload.loaded_ammo >= 5) then
                    context:runAnimation("reload_loop_clip_empty", track, false, PLAY_ONCE_HOLD, 0)
                    main_track_states.reload.loaded_ammo = main_track_states.reload.loaded_ammo + 5
                else
                    context:runAnimation("reload_loop_empty", track, false, PLAY_ONCE_HOLD, 0)
                    main_track_states.reload.loaded_ammo = main_track_states.reload.loaded_ammo + 1
                end
                main_track_states.reload.emptyload = 0
            else
                if (main_track_states.reload.need_ammo - main_track_states.reload.loaded_ammo >= 5) then
                    context:runAnimation("reload_loop_clip", track, false, PLAY_ONCE_HOLD, 0)
                    main_track_states.reload.loaded_ammo = main_track_states.reload.loaded_ammo + 5
                else
                    context:runAnimation("reload_loop", track, false, PLAY_ONCE_HOLD, 0)
                    main_track_states.reload.loaded_ammo = main_track_states.reload.loaded_ammo + 1
                end
            end
        end
    end
end

function main_track_states.reload.transition(this, context, input)
    if (input == main_track_states.reload.retreat or input == INPUT_CANCEL_RELOAD) then
        context:runAnimation("reload_end", context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK), false, PLAY_ONCE_STOP, 0.1)
        main_track_states.reload.emptyload = 0
        return main_track_states.idle
    end
    return this.main_track_states.idle.transition(this, context, input)
end


function main_track_states.inspect.entry(this, context)
    context:setShouldHideCrossHair(true)
end

function main_track_states.inspect.exit(this, context)
    context:setShouldHideCrossHair(false)
end

function main_track_states.inspect.update(this, context)
    if (context:isStopped(context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK))) then
        context:trigger(main_track_states.inspect.retreat)
    end
end

function main_track_states.inspect.transition(this, context, input)
    if (input == main_track_states.inspect.retreat) then
        return main_track_states.idle
    end
    if (input == INPUT_SHOOT) then -- 特殊地，射击也应当打断检视
        context:stopAnimation(context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK))
        return main_track_states.idle
    end
    return this.main_track_states.idle.transition(this, context, input)
end

local gun_kick_state = {}

function gun_kick_state.transition(this, context, input)
    if (input == INPUT_SHOOT) then
        local track = context:findIdleTrack(GUN_KICK_TRACK_LINE, false)
        if (context:getAttachment("MUZZLE") ~= "hamster:gas_tube") then
            if (context:getAttachment("SCOPE") ~= "tacz:empty") then
                context:runAnimation("shoot_scope", track, true, PLAY_ONCE_STOP, 0)
            else
                context:runAnimation("shoot", track, true, PLAY_ONCE_STOP, 0)
            end
        else
            if (context:getAttachment("SCOPE") ~= "tacz:empty") then
                context:runAnimation("shoot_howell_scope", track, true, PLAY_ONCE_STOP, 0)
            else
                context:runAnimation("shoot_howell", track, true, PLAY_ONCE_STOP, 0)
            end
        end
        if (context:getAimingProgress() == 0) then
            main_track_states.shoot_mode = 1
        else
            main_track_states.shoot_mode = 0
        end
    end
    return nil
end

-- 移动轨道的状态,这部分到 450 行结束
local movement_track_states = {
    -- 静止不动(或者在天上)
    idle = {},
    -- 奔跑, -1 是没有奔跑, 0 是在奔跑中
    run = {
        mode = -1,
        time = 0
    },
    -- 行走, -1 是没有行走, 0 是在空中, 1 是正在瞄准, 2 是在向前走, 3 是向后退, 4 是向侧面走
    walk = {
        mode = -1
    }
}

-- 更新静止态
function movement_track_states.idle.update(this, context)
    -- 此处获取的是混合轨道行的移动轨道
    local track = context:getTrack(BLENDING_TRACK_LINE, MOVEMENT_TRACK)
    -- 如果轨道空闲，则播放 idle 动画
    -- 注意此处没有写成是在 entry 播放 idle 动画是因为要实时检测轨道是否空闲
    if (context:isStopped(track) or context:isHolding(track)) then
        context:runAnimation("idle", track, true, LOOP, 0)
    end
end

-- 转出静止态
function movement_track_states.idle.transition(this, context, input)
    -- 如果玩家在奔跑则转去奔跑态
    if (input == INPUT_RUN) then
        if (context:isStopped(context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK))) then
            return this.movement_track_states.run
        else
            return this.movement_track_states.walk
        end
        -- 如果玩家在行走则转去行走态
    elseif (input == INPUT_WALK) then
        return this.movement_track_states.walk
    end
end

-- 进入奔跑态
function movement_track_states.run.entry(this, context)
    this.movement_track_states.run.mode = -1
    this.movement_track_states.run.time = context:getCurrentTimestamp()
    -- 此处播放的轨道是混合轨道行的移动轨道,播放的动画是奔跑的起手式,播放结束后是挂起动画而不是停止
    context:runAnimation("run_start", context:getTrack(BLENDING_TRACK_LINE, MOVEMENT_TRACK), true, PLAY_ONCE_HOLD, 0.2)
end

-- 退出奔跑态
function movement_track_states.run.exit(this, context)
    -- 此时播放的动画是奔跑结束回到 idle 的动画,同理播放完后挂起
    context:runAnimation("run_end", context:getTrack(BLENDING_TRACK_LINE, MOVEMENT_TRACK), true, PLAY_ONCE_HOLD, 0.3)
end

-- 更新奔跑态
function movement_track_states.run.update(this, context)
    local track = context:getTrack(BLENDING_TRACK_LINE, MOVEMENT_TRACK)
    local state = this.movement_track_states.run;
    -- 等待 run_start 结束,然后循环播放 run ,此处的判断准则是轨道是否挂起,也就是为什么 entry 里播放动画要选 PLAY_ONCE_HOLD 模式
    if (context:isHolding(track)) then
        context:runAnimation("run", track, true, LOOP, 0.2)
        -- 检测是否奔跑的标志位 0
        state.mode = 0
        context:anchorWalkDist() -- 打 walkDist 锚点，确保 run 动画的起点一致
    end
    if (state.mode ~= -1) then
        if (not context:isOnGround()) then
            -- 如果玩家在空中，则播放 run_hold 动画以稳定枪身
            if (state.mode ~= 1) then
                state.mode = 1
                context:runAnimation("run_hold", track, true, LOOP, 0.6)
            end
        else
            -- 如果玩家在地面，则切换回 run 动画
            if (state.mode ~= 0) then
                state.mode = 0
                context:runAnimation("run", track, true, LOOP, 0.2)
            end
            -- 根据 walkDist 设置 run 动画的进度
            context:setAnimationProgress(track, (context:getWalkDist() % 2.0) / 2.0, true)
        end
    end
end

-- 转出奔跑态
function movement_track_states.run.transition(this, context, input)
    -- 收到闲置输入则转去闲置态
    if (input == INPUT_IDLE) then
        return this.movement_track_states.idle
        -- 收到行走输入则转去行走态
    elseif (input == INPUT_WALK or not context:isStopped(context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK))) then
        return this.movement_track_states.walk
    end
end


-- 进入行走态
function movement_track_states.walk.entry(this, context)
    -- 此时给标志位置为 -1 相当于一个初始化
    this.movement_track_states.walk.mode = -1
end

-- 退出行走态
function movement_track_states.walk.exit(this, context)
    -- 手动播放一次 idle 动画以打断 walk 动画的循环
    context:runAnimation("idle", context:getTrack(BLENDING_TRACK_LINE, MOVEMENT_TRACK), true, PLAY_ONCE_HOLD, 0.4)
end

-- 更新行走态
function movement_track_states.walk.update(this, context)
    -- 此处获取的是混合轨道行的移动轨道
    local track = context:getTrack(BLENDING_TRACK_LINE, MOVEMENT_TRACK)
    -- 这里的 state 代指自身,相当于一个简化写法
    local state = this.movement_track_states.walk
    if (context:getShootCoolDown() > 0) then
        -- 如果刚刚开火，则播放 idle 动画以稳定枪身
        if (state.mode ~= 0) then
            state.mode = 0
            context:runAnimation("idle", track, true, LOOP, 0.3)
        end
    elseif (not context:isOnGround()) then
        -- 如果玩家在空中，则播放 idle 动画以稳定枪身
        if (state.mode ~= 0) then
            state.mode = 0
            context:runAnimation("idle", track, true, LOOP, 0.6)
        end
    elseif (context:getAimingProgress() > 0.5) then
        -- 如果正在喵准，则需要播放 walk_aiming 动画
        if (state.mode ~= 1) then
            state.mode = 1
            context:runAnimation("walk_aiming", track, true, LOOP, 0.3)
        end
    elseif (context:isInputUp()) then
        -- 如果正在向前走，则需要播放 walk_forward 动画
        if (state.mode ~= 2) then
            state.mode = 2
            context:runAnimation("walk_forward", track, true, LOOP, 0.4)
            context:anchorWalkDist() -- 打 walkDist 锚点，确保行走动画的起点一致
        end
    elseif (context:isInputDown()) then
        -- 如果正在向后退，则需要播放 walk_backward 动画
        if (state.mode ~= 3) then
            state.mode = 3
            context:runAnimation("walk_backward", track, true, LOOP, 0.4)
            context:anchorWalkDist() -- 打 walkDist 锚点，确保行走动画的起点一致
        end
    elseif (context:isInputLeft() or context:isInputRight()) then
        -- 如果正在向侧面，则需要播放 walk_sideway 动画
        if (state.mode ~= 4) then
            state.mode = 4
            context:runAnimation("walk_sideway", track, true, LOOP, 0.4)
            context:anchorWalkDist() -- 打 walkDist 锚点，确保行走动画的起点一致
        end
    end
    -- 根据 walkDist 设置行走动画的进度
    if (state.mode >= 1 and state.mode <= 4) then
        context:setAnimationProgress(track, (context:getWalkDist() % 2.0) / 2.0, true)
    end
end

-- 转出行走态,这部分和转出奔跑态是一样的
function movement_track_states.walk.transition(this, context, input)
    -- 收到闲置信号则转到闲置态
    if (input == INPUT_IDLE) then
        return this.movement_track_states.idle
        -- 收到奔跑信号则转到奔跑态
    elseif (input == INPUT_RUN) then
        if (context:isStopped(context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK))) then
            return this.movement_track_states.run
        end
    end
end
-- 结束移动轨道的状态

local ADS_states = {
    aiming_progress = 0,-- 记录瞄准进度
    normal = {},-- 不瞄准状态
    aiming = {}-- 瞄准状态
}

-- 进入不瞄准状态
function ADS_states.normal.entry(this, context)
    this.ADS_states.normal.update(this, context)
end

-- 更新不瞄准状态
function ADS_states.normal.update(this, context)
    -- 当瞄准进度正在增加时转到瞄准状态
    if (context:getAttachment("MUZZLE") == "hamster:gas_tube" and context:getAttachment("SCOPE") == "tacz:empty") then
        if (context:getAimingProgress() > this.ADS_states.aiming_progress or context:getAimingProgress() == 1) then
            context:trigger(this.INPUT_AIM)
        else
            -- 如果没有增加，则记录当前的瞄准进度
            this.ADS_states.aiming_progress = context:getAimingProgress()
        end
    end
end

-- 转出不瞄准状态
function ADS_states.normal.transition(this, context, input)
    -- 接收到上文 update 方法的输入，则转到瞄准状态
    if (input == this.INPUT_AIM) then
        return this.ADS_states.aiming
    end
end

-- 进入瞄准状态
function ADS_states.aiming.entry(this, context)
    -- 开始瞄准时播放瞄准动画，并且将其挂起
    local track = context:getTrack(BLENDING_TRACK_LINE, ADS_TRACK)
    context:runAnimation("aim_start", track, true, PLAY_ONCE_HOLD, 0.2)
    -- 打断检视动画
    context:trigger(this.INPUT_INSPECT_RETREAT)
end

-- 更新瞄准状态
function ADS_states.aiming.update(this, context)
    local track = context:getTrack(BLENDING_TRACK_LINE, ADS_TRACK)
    if (context:isHolding(track)) then
        -- 循环播放瞄准时的动画
        context:runAnimation("aim", track, true, PLAY_ONCE_HOLD, 0.2)
    end
    -- 当瞄准进度正在减小时转到不瞄准状态，也即取消瞄准
    if (context:getAimingProgress() < this.ADS_states.aiming_progress) then
        context:trigger(this.INPUT_AIM_RETREAT)
    else
        -- 如果没有减小，则记录当前瞄准进度
        this.ADS_states.aiming_progress = context:getAimingProgress()
    end
end

-- 转出瞄准状态
function ADS_states.aiming.transition(this, context, input)
    local track = context:getTrack(BLENDING_TRACK_LINE, ADS_TRACK)
    if (input == this.INPUT_AIM_RETREAT) then
        --播放瞄准结束动画，并调整动画进度使开镜动画与当前的开镜进度相对应
        context:runAnimation("aim_end", track, true, PLAY_ONCE_STOP, 0.2)
        return this.ADS_states.normal
    end
end

local M = {
    -- track lines
    track_line_top = track_line_top,
    STATIC_TRACK_LINE = STATIC_TRACK_LINE,
    GUN_KICK_TRACK_LINE = GUN_KICK_TRACK_LINE,
    BLENDING_TRACK_LINE = BLENDING_TRACK_LINE,
    -- static tracks
    static_track_top = static_track_top,
    BASE_TRACK = BASE_TRACK,
    BOLT_CAUGHT_TRACK = BOLT_CAUGHT_TRACK,
    SAFETY_TRACK = SAFETY_TRACK,
    ADS_TRACK = ADS_TRACK,
    MAIN_TRACK = MAIN_TRACK,
    -- blending tracks
    blending_track_top = blending_track_top,
    MOVEMENT_TRACK = MOVEMENT_TRACK,
    LOOP_TRACK = LOOP_TRACK,
    -- states
    base_track_state = base_track_state,
    bolt_caught_states = bolt_caught_states,
    main_track_states = main_track_states,
    gun_kick_state = gun_kick_state,
    movement_track_states = movement_track_states,
    ADS_states = ADS_states,
    -- inputs
    INPUT_BOLT_CAUGHT = "bolt_caught",
    INPUT_BOLT_NORMAL = "bolt_normal",
    INPUT_INSPECT_RETREAT = "inspect_retreat",
    INPUT_AIM = "aim",
    INPUT_AIM_RETREAT = "aim_retreat"
}

function M:initialize(context)
    context:ensureTrackLineSize(track_line_top.value)
    context:ensureTracksAmount(STATIC_TRACK_LINE, static_track_top.value)
    context:ensureTracksAmount(BLENDING_TRACK_LINE, blending_track_top.value)
    self.movement_track_states.run.mode = -1
    self.movement_track_states.walk.mode = -1
end

function M:exit(context)
    -- do some cleaning up things
end

function M:states()
    return {
        self.base_track_state,
        self.bolt_caught_states.normal,
        self.main_track_states.start,
        self.gun_kick_state,
        self.movement_track_states.idle,
        self.ADS_states.normal
    }
end

return M