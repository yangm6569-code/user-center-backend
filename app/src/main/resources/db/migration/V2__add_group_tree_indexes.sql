create index if not exists idx_uc_group_parent on uc_group (parent_id);
create index if not exists idx_uc_group_path on uc_group (path);
create index if not exists idx_uc_user_group_group on uc_user_group (group_id);
create index if not exists idx_uc_group_role_group on uc_group_role (group_id);
create index if not exists idx_uc_group_role_role on uc_group_role (role_id);
