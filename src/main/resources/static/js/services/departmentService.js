// ==================== Department Service ====================
import { request } from '../core/api.js';
import { isAdmin, isGiamDoc, hasRole } from '../core/auth.js';

let selectedDepartment = null;
let selectedDepartmentForDelete = null;
let managersCache = [];

export const getSelectedDepartment = () => selectedDepartment;
export const setSelectedDepartment = (dept) => { selectedDepartment = dept; };
export const getSelectedDepartmentForDelete = () => selectedDepartmentForDelete;
export const setSelectedDepartmentForDelete = (dept) => { selectedDepartmentForDelete = dept; };
export const getManagersCache = () => managersCache;
export const setManagersCache = (cache) => { managersCache = cache; };

export const loadDepartments = async () => {
  try {
    const depts = await request('/api/departments');
    populateDepartmentSelect(document.getElementById('admin-dept-select'), depts);
    return depts;
  } catch (error) {
    return [];
  }
};

export const populateDepartmentSelect = (select, departments) => {
  if (!select) return;
  select.innerHTML = '';
  
  const option = document.createElement('option');
  option.value = '';
  option.textContent = '— Chọn phòng ban —';
  select.appendChild(option);
  
  departments.forEach((dept) => {
    const option = document.createElement('option');
    option.value = dept.id;
    option.textContent = `${dept.code} - ${dept.name}`;
    option.dataset.code = dept.code;
    select.appendChild(option);
  });
};

export const loadDepartmentsTable = async () => {
  if (!isAdmin() && !isGiamDoc()) return;
  const deptTbody = document.getElementById('departments-tbody');
  if (!deptTbody) return;

  try {
    const departments = await request('/api/departments/all');
    
    deptTbody.innerHTML = '';

    if (departments.length === 0) {
      deptTbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:var(--muted)">Chưa có phòng ban nào.</td></tr>';
      return;
    }

    departments.forEach((dept) => {
      const row = document.createElement('tr');
      const statusClass = dept.enabled ? 'status-active' : 'status-disabled';
      const statusBadge = dept.enabled ? 'Hoạt động' : 'Vô hiệu hoá';
      const managerName = dept.managerName || '—';
      const userCount = dept.userCount || 0;
      const description = dept.description || '—';

      row.innerHTML = `
        <td><strong>${dept.code}</strong></td>
        <td>${dept.name}</td>
        <td title="${description}">${description.length > 30 ? description.substring(0, 30) + '...' : description}</td>
        <td>${managerName}</td>
        <td>${userCount}</td>
        <td><span class="status-badge ${statusClass}">${statusBadge}</span></td>
        <td>
          <button class="btn-edit-dept btn-small secondary" data-dept-id="${dept.id}">Sửa</button>
          <button class="btn-delete-dept btn-small ghost" data-dept-id="${dept.id}" data-dept-name="${dept.name}">Xóa</button>
        </td>
      `;
      deptTbody.appendChild(row);
    });

    deptTbody.querySelectorAll('.btn-edit-dept').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const deptId = btn.dataset.deptId;
        const dept = departments.find(d => d.id == deptId);
        if (dept) openEditDeptModal(dept);
      });
    });

    deptTbody.querySelectorAll('.btn-delete-dept').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const deptId = btn.dataset.deptId;
        const deptName = btn.dataset.deptName;
        const dept = departments.find(d => d.id == deptId);
        if (dept) openDeleteDeptModal(dept);
      });
    });
  } catch (error) {
    console.error('Error loading departments:', error);
  }
};

export const openCreateDeptModal = () => {
  selectedDepartment = null;
  const deptModal = document.getElementById('dept-modal');
  const deptModalTitle = document.getElementById('dept-modal-title');
  const deptModalDesc = document.getElementById('dept-modal-desc');
  const deptId = document.getElementById('dept-id');
  const deptCode = document.getElementById('dept-code');
  const deptName = document.getElementById('dept-name');
  const deptDescription = document.getElementById('dept-description');
  const deptEnabled = document.getElementById('dept-enabled');
  const deptSubmitBtn = document.getElementById('dept-submit-btn');
  
  deptModalTitle.textContent = 'Tạo phòng ban';
  deptModalDesc.textContent = 'Thêm một phòng ban mới vào hệ thống.';
  deptId.value = '';
  deptCode.value = '';
  deptCode.disabled = false;
  deptName.value = '';
  deptDescription.value = '';
  deptEnabled.checked = true;
  const deptEnabledLabel = document.getElementById('dept-enabled-label');
  if (deptEnabledLabel) deptEnabledLabel.classList.add('hidden');
  deptSubmitBtn.textContent = 'Tạo phòng ban';
  deptModal.classList.remove('hidden');
  deptModal.setAttribute('aria-hidden', 'false');
  deptCode.focus();
  
  loadManagersForDepartment();
};

export const openEditDeptModal = (dept) => {
  selectedDepartment = dept;
  const deptModal = document.getElementById('dept-modal');
  const deptModalTitle = document.getElementById('dept-modal-title');
  const deptModalDesc = document.getElementById('dept-modal-desc');
  const deptId = document.getElementById('dept-id');
  const deptCode = document.getElementById('dept-code');
  const deptName = document.getElementById('dept-name');
  const deptDescription = document.getElementById('dept-description');
  const deptEnabled = document.getElementById('dept-enabled');
  const deptSubmitBtn = document.getElementById('dept-submit-btn');
  
  deptModalTitle.textContent = 'Sửa phòng ban';
  deptModalDesc.textContent = `Cập nhật thông tin phòng ban "${dept.name}".`;
  deptId.value = dept.id;
  deptCode.value = dept.code;
  deptCode.disabled = true;
  deptName.value = dept.name;
  deptDescription.value = dept.description || '';
  deptEnabled.checked = dept.enabled;
  const deptEnabledLabel = document.getElementById('dept-enabled-label');
  if (deptEnabledLabel) deptEnabledLabel.classList.remove('hidden');
  deptSubmitBtn.textContent = 'Lưu thay đổi';
  deptModal.classList.remove('hidden');
  deptModal.setAttribute('aria-hidden', 'false');
  deptName.focus();
  
  loadManagersForDepartment(dept.managerId);
};

export const loadManagersForDepartment = async (selectedId = null) => {
  const deptManager = document.getElementById('dept-manager');
  if (!deptManager) return;
  
  deptManager.innerHTML = '<option value="">-- Chọn trưởng phòng --</option>';
  
  try {
    const response = await request('/api/users?page=0&size=100');
    
    let users = [];
    if (Array.isArray(response)) {
      users = response;
    } else if (response && typeof response === 'object') {
      users = response.content || response.users || Object.values(response)[0] || [];
    }
    
    managersCache = users;
    
    for (const user of managersCache) {
      if (user.role === 'ADMIN' || user.role === 'GIAM_DOC') continue;
      
      const option = document.createElement('option');
      option.value = user.id;
      option.textContent = `${user.displayName || user.username} (${user.username})`;
      if (selectedId && user.id === selectedId) {
        option.selected = true;
      }
      deptManager.appendChild(option);
    }
  } catch (e) {
    console.error('Error loading managers:', e);
  }
};

export const openDeleteDeptModal = (dept) => {
  selectedDepartmentForDelete = dept;
  const userCount = dept.userCount || 0;
  
  const deptDeleteWarning = document.getElementById('dept-delete-warning');
  const deptDeleteAffected = document.getElementById('dept-delete-affected');
  const deptDeleteModal = document.getElementById('dept-delete-modal');
  
  if (deptDeleteWarning) {
    deptDeleteWarning.textContent = `Bạn có chắc muốn xóa phòng ban "${dept.name}" (${dept.code})?`;
  }
  
  if (deptDeleteAffected) {
    if (userCount > 0) {
      deptDeleteAffected.innerHTML = `
        <strong>Cảnh báo:</strong> Phòng ban này có <strong>${userCount} nhân viên</strong>. 
        Khi xóa, tất cả nhân viên sẽ được gỡ khỏi phòng ban này (phòng ban = null).
      `;
      deptDeleteAffected.classList.remove('hidden');
    } else {
      deptDeleteAffected.classList.add('hidden');
    }
  }
  
  if (deptDeleteModal) {
    deptDeleteModal.classList.remove('hidden');
    deptDeleteModal.setAttribute('aria-hidden', 'false');
  }
};

export const closeDeptModalHandler = () => {
  const deptModal = document.getElementById('dept-modal');
  if (document.activeElement && deptModal.contains && deptModal.contains(document.activeElement)) {
    document.activeElement.blur();
  }
  if (deptModal) {
    deptModal.classList.add('hidden');
    deptModal.setAttribute('aria-hidden', 'true');
  }
  selectedDepartment = null;
};

export const closeDeptDeleteModalHandler = () => {
  const deptDeleteModal = document.getElementById('dept-delete-modal');
  if (deptDeleteModal) {
    deptDeleteModal.classList.add('hidden');
    deptDeleteModal.setAttribute('aria-hidden', 'true');
  }
  selectedDepartmentForDelete = null;
};

export const loadDepartmentsForSelects = async () => {
  try {
    const depts = await request('/api/departments');
    const adminDeptSelect = document.getElementById('admin-dept-select');
    const userDeptSelect = document.getElementById('user-dept-select');
    
    if (adminDeptSelect) {
      populateDepartmentSelect(adminDeptSelect, depts);
    }
    if (userDeptSelect) {
      populateDepartmentSelect(userDeptSelect, depts);
    }
  } catch (error) {
    console.error('Error loading departments for selects:', error);
  }
};

// Export state variables for event handlers
export { selectedDepartment, selectedDepartmentForDelete };
