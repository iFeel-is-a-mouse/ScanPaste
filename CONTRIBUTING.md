# ScanPaste — 团队协作规范

## 分支策略

- `main` 分支为唯一工作分支（单人项目，无需 feature/release 分支）
- 每个 agent 完成任务后提交，commit message 格式：`[agent] 阶段X: 简述`

## Commit 规范

```
[agent] 阶段X: 做了什么
```

示例：
- `[main] 阶段1: 编写spec+style-guide`
- `[coder] 阶段6: 实现MainActivity和UndoRedo逻辑`
- `[tester] 阶段7: 完成UndoRedo单元测试`

## Code Review 流程

- main 在每个 agent 交付后进行教练级审查
- auditor 在阶段2(前置)和阶段8(终审)执行正式审计

## 禁止事项

- 不直接在 main 分支做破坏性修改
- agent 之间不互相提交代码（通过 main 协调）
