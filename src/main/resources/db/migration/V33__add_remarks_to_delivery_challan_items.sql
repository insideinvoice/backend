-- Royal delivery-challan template prints a per-item Remark;
-- Classic stays column-free, the field is simply optional/unused there.
ALTER TABLE delivery_challan_items ADD COLUMN remarks VARCHAR(300);
